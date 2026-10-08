package com.momna.platform.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AiContracts {
    private AiContracts() {}

    public record TokenUsage(long inputTokens, long outputTokens) {
        public TokenUsage {
            if (inputTokens < 0 || outputTokens < 0) throw new IllegalArgumentException("Token usage must be non-negative");
        }
        public long totalTokens() { return inputTokens + outputTokens; }
    }

    public record Cost(String currency, long amountMicros) {
        public Cost {
            if (!"USD_MICROS".equals(currency) || amountMicros < 0) {
                throw new IllegalArgumentException("Invalid AI cost");
            }
        }
        public static Cost usdMicros(long amount) { return new Cost("USD_MICROS", amount); }
    }

    public record Attempt(String candidateRef, AiFailureCategory failureCategory) {}

    public record Request(
        String purpose,
        String contextSnapshotId,
        String promptKey,
        Integer promptVersion,
        String modelPolicyKey,
        Integer modelPolicyVersion,
        Map<String,String> variables,
        String requestId,
        String idempotencyKey,
        String traceId
    ) {}

    public record Response(
        String text,
        String modelRef,
        int promptVersion,
        int modelPolicyVersion,
        int outputSchemaVersion,
        AiFallbackState fallbackState,
        AiCacheStatus cacheStatus,
        TokenUsage usage,
        Cost cost,
        List<Attempt> attempts
    ) {}

    public record ContextEnvelope(
        String snapshotId,
        long snapshotVersion,
        String subjectUserId,
        String purpose,
        int purposeVersion,
        int contextSchemaVersion,
        int outputSchemaVersion,
        String fingerprint,
        int projectionSchemaVersion,
        String projectionFingerprint
    ) {}

    public record PromptDefinition(
        String key,
        int version,
        String template,
        Set<String> requiredVariables,
        AiOutputSchemaKind outputSchemaKind,
        int outputSchemaVersion
    ) {}

    public record ModelCandidate(
        String candidateRef,
        String providerKey,
        String modelKey,
        long inputUsdMicrosPerThousandTokens,
        long outputUsdMicrosPerThousandTokens
    ) {}

    public record CachePolicy(boolean enabled, Duration ttl, boolean contentStorageAllowed) {}

    public record BudgetPolicy(
        long maxCostMicrosPerRequest,
        long maxTotalTokensPerRequest,
        long maxCostMicrosPerSubjectPurposeWindow,
        String windowKey
    ) {}

    public record ModelRoutingPolicy(
        String key,
        int version,
        List<ModelCandidate> candidates,
        int maxAttempts,
        Set<AiFailureCategory> retryableCategories,
        boolean fallbackOnMalformedOutput,
        CachePolicy cachePolicy,
        BudgetPolicy budgetPolicy
    ) {}

    public record PolicyDecision(
        boolean allowed,
        String privacyPolicyVersion,
        String safetyPolicyVersion,
        String reasonCode
    ) {}

    public record ProviderResult(String text, TokenUsage usage) {}
}
