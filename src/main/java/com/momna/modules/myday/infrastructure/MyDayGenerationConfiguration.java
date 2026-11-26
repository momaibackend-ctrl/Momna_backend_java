package com.momna.modules.myday.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.myday.application.MyDayCardSetParser;
import com.momna.modules.myday.application.MyDayCardSource;
import com.momna.modules.myday.application.MyDayException;
import com.momna.modules.myday.application.generation.*;
import com.momna.platform.ai.*;
import com.momna.platform.ai.AiContracts.*;
import com.momna.platform.cache.ExpiringCache;
import com.momna.platform.cache.InMemoryExpiringCache;
import com.momna.platform.safety.CanonicalSafetyGuardedAi;
import com.momna.platform.safety.CanonicalSafetyService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the My Day LLM path (MOMNA-849) when {@code momna.ai.myday.enabled=true}: OpenAI-compatible
 * provider -> canonical AI gateway -> SafetyGuardedAi -> {@link LlmMyDayCardSource}. Disabled, there is no
 * card source and generation fails closed with 503, as before.
 *
 * <p>Model policy v1: one model, two attempts (the spec's «one retry»), 15 s timeout, retry on timeout,
 * rate limit, provider error and an invalid answer. The gateway's own result cache is off: only the
 * anonymous template cache may share an answer.
 */
@Configuration
@ConditionalOnProperty(name = "momna.ai.myday.enabled", havingValue = "true")
public class MyDayGenerationConfiguration {
    public static final int MODEL_POLICY_VERSION = 1;

    @Bean
    MyDayCardSource llmMyDayCardSource(
        JdbcTemplate jdbc,
        CanonicalSafetyService safety,
        ObjectProvider<ExpiringCache> caches,
        @Value("${momna.ai.provider.base-url:}") String baseUrl,
        @Value("${momna.ai.provider.api-key:}") String apiKey,
        @Value("${momna.ai.provider.timeout:15s}") Duration timeout,
        @Value("${momna.ai.myday.model:gpt-4.1-mini}") String model,
        @Value("${momna.ai.myday.temperature:0.7}") double temperature,
        @Value("${momna.ai.myday.input-usd-micros-per-1k-tokens:400}") long inputPrice,
        @Value("${momna.ai.myday.output-usd-micros-per-1k-tokens:1600}") long outputPrice,
        @Value("${momna.ai.myday.max-cost-usd-micros-per-request:50000}") long maxRequestCost,
        @Value("${momna.ai.myday.max-tokens-per-request:16000}") long maxRequestTokens,
        @Value("${momna.ai.myday.max-cost-usd-micros-per-user-day:500000}") long maxUserDayCost,
        @Value("${momna.ai.myday.template-ttl:90d}") Duration templateTtl
    ) {
        var clock = Clock.systemUTC();
        var provider = new OpenAiCompatibleProviderAdapter(baseUrl, apiKey, timeout, temperature);
        var candidates = List.of(
            new ModelCandidate(OpenAiCompatibleProviderAdapter.PROVIDER_KEY + "/" + model + "#1",
                OpenAiCompatibleProviderAdapter.PROVIDER_KEY, model, inputPrice, outputPrice),
            new ModelCandidate(OpenAiCompatibleProviderAdapter.PROVIDER_KEY + "/" + model + "#2",
                OpenAiCompatibleProviderAdapter.PROVIDER_KEY, model, inputPrice, outputPrice));
        var policy = new ModelRoutingPolicy(LlmMyDayCardSource.MODEL_POLICY_KEY, MODEL_POLICY_VERSION, candidates, 2,
            Set.of(AiFailureCategory.TIMEOUT, AiFailureCategory.RATE_LIMIT, AiFailureCategory.PROVIDER_UNAVAILABLE,
                AiFailureCategory.MALFORMED_OUTPUT),
            true, new CachePolicy(false, Duration.ZERO, false),
            new BudgetPolicy(maxRequestCost, maxRequestTokens, maxUserDayCost, JdbcAiCostLedger.UTC_DAY));

        var parser = new MyDayCardSetParser(new ObjectMapper());
        AiPorts.OutputValidator validator = new AiPorts.OutputValidator() {
            @Override public AiOutputSchemaKind kind() { return AiOutputSchemaKind.MY_DAY_CARDS_V1; }
            @Override public boolean valid(String text) {
                try {
                    parser.parse(text);
                    return true;
                } catch (MyDayException invalid) {
                    return false;
                }
            }
        };
        var context = new MyDayAiContext();
        var gateway = new CanonicalAiGateway(context, context, new StaticAiPromptRegistry(MyDayPromptCatalog.load()),
            new StaticAiModelRegistry(List.of(policy)), new StaticAiProviderRegistry(List.of(provider)),
            caches.getIfAvailable(InMemoryExpiringCache::new), new JdbcAiCostLedger(jdbc, clock), List.of(validator));
        return new LlmMyDayCardSource(new CanonicalSafetyGuardedAi(safety, gateway), context,
            new MyDayTemplateCache(jdbc, clock), parser, MODEL_POLICY_VERSION, templateTtl);
    }
}
