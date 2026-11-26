package com.momna.modules.myday.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.momna.modules.myday.application.MyDayCardSetParser;
import com.momna.modules.myday.application.MyDayCardSource;
import com.momna.modules.myday.application.MyDayException;
import com.momna.modules.myday.application.generation.*;
import com.momna.modules.myday.application.template.MyDayTemplateKey;
import com.momna.platform.ai.*;
import com.momna.platform.ai.AiContracts.*;
import com.momna.platform.cache.InMemoryExpiringCache;
import com.momna.platform.safety.CanonicalSafetyGuardedAi;
import com.momna.platform.safety.CanonicalSafetyService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * The My Day LLM path through the real canonical gateway with a scripted provider: template cache first,
 * one retry, only a valid 3+3+3 answer is cached and returned, anything else fails closed.
 */
class LlmMyDayCardSourceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VALID = cards(3).toString();
    private static final String EIGHT_CARDS = cards(2).toString();

    private final List<String> prompts = new CopyOnWriteArrayList<>();
    private final Deque<Object> script = new ArrayDeque<>();
    private final MemoryTemplateCache templates = new MemoryTemplateCache();
    private final MemoryLedger ledger = new MemoryLedger();
    private final MyDayAiContext context = new MyDayAiContext();
    private final MyDayCardSetParser parser = new MyDayCardSetParser(JSON);
    private final LlmMyDayCardSource source = source(50_000);

    @Test
    void aMissCallsTheModelOnceAndTheSameKeyIsThenServedFromTheTemplateCache() {
        script.add(VALID);
        var day = source.generate(input("user-a", key("adult_cycle")));
        assertEquals(LlmMyDayCardSource.CARD_SOURCE_LLM, day.cardSource());
        assertEquals(9, day.cards().size());
        assertEquals("morning 1 title", day.cards().getFirst().title());
        assertEquals(MyDayTemplateKey.PROMPTS_VERSION, day.promptVersion());
        assertEquals("openai-compatible/test-model#1", day.modelRef());
        assertEquals(key("adult_cycle").fingerprint(), day.cacheKey());
        assertEquals(1, prompts.size());

        var sections = AiPromptSections.split(prompts.getFirst());
        assertTrue(sections.system().contains("adult woman"), sections.system());
        assertTrue(sections.user().contains("life_mode: adult_cycle\n"), sections.user());
        assertTrue(sections.user().contains("overlay_keys: none\n"), sections.user());
        assertTrue(sections.user().contains("tone: friendly\n"), sections.user());
        assertFalse(prompts.getFirst().contains("{{"), "every variable is rendered");
        assertFalse(prompts.getFirst().contains("user-a"), "no user id reaches the prompt");

        var other = source.generate(input("user-b", key("adult_cycle")));
        assertEquals(LlmMyDayCardSource.CARD_SOURCE_TEMPLATE, other.cardSource());
        assertEquals(day.cards(), other.cards());
        assertEquals(1, prompts.size(), "a template hit does not call the model");
        assertEquals(1, ledger.records.size());
    }

    @Test
    void anInvalidFirstAnswerIsRetriedOnce() {
        script.add(EIGHT_CARDS);
        script.add(VALID);
        var day = source.generate(input("user-a", key("planning")));
        assertEquals(9, day.cards().size());
        assertEquals("openai-compatible/test-model#2", day.modelRef());
        assertEquals(2, prompts.size());
        assertEquals(1, templates.rows.size());
    }

    @Test
    void twoInvalidAnswersFailClosedAndNothingIsCached() {
        script.add(EIGHT_CARDS);
        script.add("{\"morning\":[]}");
        assertUnavailable(() -> source.generate(input("user-a", key("menopause"))));
        assertEquals(2, prompts.size());
        assertTrue(templates.rows.isEmpty());
    }

    @Test
    void timeoutsAndProviderErrorsAreRetriedOnceThenFailClosed() {
        script.add(new AiProviderException(AiFailureCategory.TIMEOUT, "timeout"));
        script.add(new AiProviderException(AiFailureCategory.PROVIDER_UNAVAILABLE, "503"));
        assertUnavailable(() -> source.generate(input("user-a", key("perimenopause"))));
        assertEquals(2, prompts.size());

        prompts.clear();
        script.add(new AiProviderException(AiFailureCategory.RATE_LIMIT, "429"));
        script.add(VALID);
        assertEquals(9, source.generate(input("user-a", key("perimenopause"))).cards().size());
        assertEquals(2, prompts.size());
    }

    @Test
    void aRefusedRequestIsNotRetried() {
        script.add(new AiProviderException(AiFailureCategory.NON_RETRYABLE, "401"));
        script.add(VALID);
        assertUnavailable(() -> source.generate(input("user-a", key("postpartum"))));
        assertEquals(1, prompts.size());
    }

    @Test
    void aSpentDailyBudgetStopsTheCallBeforeTheModel() {
        var tight = source(1);
        ledger.spent = 1;
        script.add(VALID);
        assertUnavailable(() -> tight.generate(input("user-a", key("teen_menarche"))));
        assertTrue(prompts.isEmpty());
    }

    @Test
    void anUnreadableTemplateRowIsEvictedAndTheDayRegenerated() {
        var key = key("pregnancy");
        templates.put(key, source.modelPolicy(), "old", EIGHT_CARDS, Duration.ofDays(1));
        script.add(VALID);
        var day = source.generate(input("user-a", key));
        assertEquals(LlmMyDayCardSource.CARD_SOURCE_LLM, day.cardSource());
        assertEquals(1, prompts.size());
        assertEquals(VALID, templates.rows.get(key.fingerprint()).cardsJson());
    }

    @Test
    void theSnapshotIsReleasedAfterEveryCall() {
        script.add(new AiProviderException(AiFailureCategory.NON_RETRYABLE, "401"));
        assertUnavailable(() -> source.generate(input("user-a", key("adult_cycle"))));
        assertEquals(0, context.activeSnapshots());
        script.add(VALID);
        source.generate(input("user-a", key("adult_cycle")));
        assertEquals(0, context.activeSnapshots());
        var probe = new Request("MY_DAY", "myday-gone", "myday.cards.adult_cycle", 3, "myday.cards", 1,
            Map.of(), "r", "i", "t");
        assertThrows(AiGatewayException.class, () -> context.resolve(probe));
    }

    private LlmMyDayCardSource source(long maxUserDayMicros) {
        var candidates = List.of(
            new ModelCandidate("openai-compatible/test-model#1", "openai-compatible", "test-model", 400, 1600),
            new ModelCandidate("openai-compatible/test-model#2", "openai-compatible", "test-model", 400, 1600));
        var policy = new ModelRoutingPolicy("myday.cards", 1, candidates, 2,
            Set.of(AiFailureCategory.TIMEOUT, AiFailureCategory.RATE_LIMIT, AiFailureCategory.PROVIDER_UNAVAILABLE,
                AiFailureCategory.MALFORMED_OUTPUT),
            true, new CachePolicy(false, Duration.ZERO, false),
            new BudgetPolicy(50_000, 16_000, maxUserDayMicros, "utc-day"));
        AiPorts.ProviderAdapter provider = new AiPorts.ProviderAdapter() {
            @Override public String providerKey() { return "openai-compatible"; }
            @Override public ProviderResult invoke(ModelCandidate candidate, String rendered, ContextEnvelope ctx, String trace) {
                prompts.add(rendered);
                var next = script.poll();
                if (next instanceof RuntimeException failure) throw failure;
                return new ProviderResult((String) next, new TokenUsage(1200, 900));
            }
        };
        AiPorts.OutputValidator validator = new AiPorts.OutputValidator() {
            @Override public AiOutputSchemaKind kind() { return AiOutputSchemaKind.MY_DAY_CARDS_V1; }
            @Override public boolean valid(String text) {
                try { parser.parse(text); return true; } catch (MyDayException e) { return false; }
            }
        };
        var gateway = new CanonicalAiGateway(context, context, new StaticAiPromptRegistry(MyDayPromptCatalog.load()),
            new StaticAiModelRegistry(List.of(policy)), new StaticAiProviderRegistry(List.of(provider)),
            new InMemoryExpiringCache(), ledger, List.of(validator));
        return new LlmMyDayCardSource(new CanonicalSafetyGuardedAi(new CanonicalSafetyService(), gateway), context,
            templates, parser, 1, Duration.ofDays(90));
    }

    private static MyDayTemplateKey key(String lifeMode) {
        return new MyDayTemplateKey(lifeMode, 2, "not_applicable", "phase_" + lifeMode.replace("_", ""), null, null,
            List.of(), "en", "friendly", MyDayTemplateKey.Versions.current());
    }

    private static MyDayCardSource.Input input(String userId, MyDayTemplateKey key) {
        return new MyDayCardSource.Input(userId, LocalDate.of(2026, 11, 26), "UTC", "CYCLE", key.lifeMode(), "en",
            "orch-" + UUID.randomUUID(), BigDecimal.ONE, List.of(), List.of(), key, List.of());
    }

    private static void assertUnavailable(Runnable call) {
        var failure = assertThrows(MyDayException.class, call::run);
        assertEquals("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", failure.code());
    }

    static ObjectNode cards(int perGroup) {
        var root = JSON.createObjectNode();
        for (var group : List.of("morning", "evening", "optional")) {
            var items = root.putArray(group);
            for (int i = 1; i <= perGroup; i++) {
                var card = items.addObject();
                for (var field : List.of("title", "microcopy", "what_to_do", "why_today", "how_to_do", "if_skipped", "support")) {
                    card.put(field, group + " " + i + " " + field);
                }
            }
        }
        return root;
    }

    static final class MemoryTemplateCache extends MyDayTemplateCache {
        final Map<String, Entry> rows = new HashMap<>();
        MemoryTemplateCache() { super(null, java.time.Clock.systemUTC()); }
        @Override public Optional<Entry> find(MyDayTemplateKey key, String modelPolicy) {
            return Optional.ofNullable(rows.get(key.fingerprint()));
        }
        @Override public void put(MyDayTemplateKey key, String modelPolicy, String modelRef, String cardsJson, Duration ttl) {
            rows.put(key.fingerprint(), new Entry(cardsJson, modelRef));
        }
        @Override public void evict(MyDayTemplateKey key, String modelPolicy) { rows.remove(key.fingerprint()); }
    }

    static final class MemoryLedger implements AiPorts.CostLedger {
        final List<String> records = new CopyOnWriteArrayList<>();
        long spent;
        @Override public long spentMicros(String subject, String purpose, String window) { return spent; }
        @Override public void record(String subject, String purpose, String requestId, String candidateRef,
                                     TokenUsage usage, Cost cost, String window) {
            records.add(candidateRef);
            spent += cost.amountMicros();
        }
    }
}
