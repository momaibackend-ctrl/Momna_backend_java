package com.momna.modules.myday.application.generation;

import com.momna.modules.myday.application.MyDayCardSetParser;
import com.momna.modules.myday.application.MyDayCardSource;
import com.momna.modules.myday.application.MyDayCards;
import com.momna.modules.myday.application.MyDayException;
import com.momna.modules.myday.application.MyDayMaterializationService;
import com.momna.modules.myday.application.template.MyDayTemplateKey;
import com.momna.platform.ai.AiContracts;
import com.momna.platform.ai.AiGatewayException;
import com.momna.platform.safety.CanonicalSafetyGuardedAi;
import com.momna.platform.safety.SafetyGuardedAiException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The production My Day source (MOMNA-849, prompts v1.2). The template key is checked in the anonymous
 * template cache first; a hit is materialized without calling the model. A miss calls the model through
 * SafetyGuardedAi and the canonical AI gateway, which times out, retries once and validates the answer
 * (exactly 3+3+3 cards with the 7 fields). Only a valid answer is cached and returned. Anything else is
 * {@code MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE}: no day is stored, and the app shows «not ready yet».
 *
 * <p>Logs cache_hit / cache_miss / llm_latency_ms / llm_error without any user identifier or content.
 */
public class LlmMyDayCardSource implements MyDayCardSource {
    public static final String CARD_SOURCE_LLM = "LLM";
    public static final String CARD_SOURCE_TEMPLATE = "TEMPLATE_CACHE";
    public static final String MODEL_POLICY_KEY = "myday.cards";

    private static final Logger log = LoggerFactory.getLogger(LlmMyDayCardSource.class);

    private final CanonicalSafetyGuardedAi ai;
    private final MyDayAiContext context;
    private final MyDayTemplateCache cache;
    private final MyDayCardSetParser parser;
    private final int modelPolicyVersion;
    private final Duration cacheTtl;

    public LlmMyDayCardSource(CanonicalSafetyGuardedAi ai, MyDayAiContext context, MyDayTemplateCache cache,
                              MyDayCardSetParser parser, int modelPolicyVersion, Duration cacheTtl) {
        this.ai = ai;
        this.context = context;
        this.cache = cache;
        this.parser = parser;
        this.modelPolicyVersion = modelPolicyVersion;
        this.cacheTtl = cacheTtl;
    }

    public String modelPolicy() {
        return MODEL_POLICY_KEY + ":v" + modelPolicyVersion;
    }

    @Override
    public MyDayCards.GeneratedDay generate(Input input) {
        var key = input.templateKey();
        if (key == null) throw unavailable("My Day has no template key");
        var policy = modelPolicy();

        var cached = cache.find(key, policy).orElse(null);
        if (cached != null) {
            try {
                var cards = parser.parse(cached.cardsJson());
                log.info("myday.generation cache_hit life_mode={} language={}", key.lifeMode(), key.userLanguage());
                return day(cards, CARD_SOURCE_TEMPLATE, cached.modelRef(), key);
            } catch (MyDayException invalid) {
                cache.evict(key, policy);
            }
        }
        log.info("myday.generation cache_miss life_mode={} language={}", key.lifeMode(), key.userLanguage());

        var snapshotId = "myday-" + UUID.randomUUID();
        var traceId = "myday-" + UUID.randomUUID();
        var idempotency = input.orchestrationId() != null ? input.orchestrationId() : input.userId() + ":" + input.localDate();
        var request = new AiContracts.Request(MyDayAiContext.PURPOSE, snapshotId, MyDayPromptCatalog.promptKey(key.lifeMode()),
            MyDayPromptCatalog.PROMPT_VERSION, MODEL_POLICY_KEY, modelPolicyVersion, MyDayPromptCatalog.variables(key),
            "myday:" + idempotency, "myday:" + idempotency + ":" + key.fingerprint(), traceId);
        long started = System.nanoTime();
        context.register(snapshotId, input.userId(), key);
        try {
            var response = ai.evaluateAndExecute(new CanonicalSafetyGuardedAi.Request(Set.of(), traceId, snapshotId, request));
            var text = response.ai().text();
            var cards = parser.parse(text);
            cache.put(key, policy, response.ai().modelRef(), text, cacheTtl);
            log.info("myday.generation llm_latency_ms={} attempts={} fallback={}", (System.nanoTime() - started) / 1_000_000,
                response.ai().attempts().size(), response.ai().fallbackState());
            return day(cards, CARD_SOURCE_LLM, response.ai().modelRef(), key);
        } catch (AiGatewayException failure) {
            log.warn("myday.generation llm_error code={} life_mode={}", failure.code(), key.lifeMode());
            throw unavailable("My Day model is unavailable");
        } catch (SafetyGuardedAiException failure) {
            log.warn("myday.generation llm_error code=SAFETY life_mode={}", key.lifeMode());
            throw unavailable("My Day generation was stopped by safety");
        } catch (MyDayException failure) {
            log.warn("myday.generation llm_error code={} life_mode={}", failure.code(), key.lifeMode());
            throw unavailable("My Day answer is invalid");
        } finally {
            context.release(snapshotId);
        }
    }

    private MyDayCards.GeneratedDay day(java.util.List<MyDayCards.Card> cards, String source, String modelRef,
                                        MyDayTemplateKey key) {
        return new MyDayCards.GeneratedDay(cards, source, MyDayMaterializationService.GENERATION_POLICY_VERSION,
            key.versions().prompts(), modelRef, com.momna.platform.safety.CanonicalSafetyService.CATALOG_VERSION,
            key.fingerprint());
    }

    private static MyDayException unavailable(String message) {
        return new MyDayException("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", message);
    }
}
