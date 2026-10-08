package com.momna.platform.ai;

import com.momna.platform.ai.AiContracts.*;
import com.momna.platform.ai.AiPorts.*;
import com.momna.platform.cache.CacheKey;
import com.momna.platform.cache.ExpiringCache;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

public class CanonicalAiGateway implements Gateway {
    private static final Pattern SAFETY_OUTPUT = Pattern.compile(
        "^[A-Z][A-Z0-9_]{1,79}\\|(NONE|LOW|MEDIUM|HIGH|CRITICAL)\\|[a-zA-Z0-9][a-zA-Z0-9._-]{2,79}$"
    );

    private final ContextAccess contextAccess;
    private final InvocationPolicyGuard policyGuard;
    private final PromptRegistry prompts;
    private final ModelRegistry models;
    private final ProviderRegistry providers;
    private final ExpiringCache cache;
    private final CostLedger costLedger;

    public CanonicalAiGateway(
        ContextAccess contextAccess,
        InvocationPolicyGuard policyGuard,
        PromptRegistry prompts,
        ModelRegistry models,
        ProviderRegistry providers,
        ExpiringCache cache,
        CostLedger costLedger
    ) {
        this.contextAccess = contextAccess;
        this.policyGuard = policyGuard;
        this.prompts = prompts;
        this.models = models;
        this.providers = providers;
        this.cache = cache;
        this.costLedger = costLedger;
    }

    @Override
    public Response execute(Request request) {
        validateRequest(request);
        var context = contextAccess.resolve(request);
        if (!context.snapshotId().equals(request.contextSnapshotId())) {
            fail(AiErrorCode.CONTEXT_NOT_FOUND, "Context snapshot mismatch");
        }
        if (!context.purpose().equals(request.purpose())) {
            fail(AiErrorCode.PURPOSE_MISMATCH, "Context purpose mismatch");
        }

        var policyDecision = policyGuard.authorize(request, context);
        if (!policyDecision.allowed()) {
            fail(AiErrorCode.PRIVACY_OR_SAFETY_DENIED, "AI invocation denied by policy");
        }

        var prompt = prompts.resolve(request.promptKey(), request.promptVersion());
        if (prompt == null) {
            fail(
                request.promptVersion() == null ? AiErrorCode.PROMPT_NOT_FOUND : AiErrorCode.PROMPT_VERSION_NOT_FOUND,
                "Prompt is not registered"
            );
        }
        var modelPolicy = models.resolvePolicy(request.modelPolicyKey(), request.modelPolicyVersion());
        if (modelPolicy == null) {
            fail(
                request.modelPolicyVersion() == null ? AiErrorCode.MODEL_POLICY_NOT_FOUND : AiErrorCode.MODEL_POLICY_VERSION_NOT_FOUND,
                "Model policy is not registered"
            );
        }

        validateModelPolicy(modelPolicy);
        var rendered = render(prompt, request.variables());
        var fingerprint = fingerprint(request, context, prompt, modelPolicy, policyDecision);
        var idempotencyKey = new CacheKey(
            "ai.idempotency", "v1",
            sha(context.subjectUserId() + "|" + request.idempotencyKey() + "|" + fingerprint)
        );

        var replay = readCached(idempotencyKey, AiCacheStatus.HIT);
        if (replay != null) return replay;

        var semanticKey = new CacheKey("ai.result", "v1", fingerprint);
        if (modelPolicy.cachePolicy().enabled() && modelPolicy.cachePolicy().contentStorageAllowed()) {
            var cached = readCached(semanticKey, AiCacheStatus.HIT);
            if (cached != null) {
                writeCached(idempotencyKey, cached, Duration.ofHours(24));
                return cached;
            }
        }

        ensureBudget(context, request, modelPolicy);
        var attempts = new ArrayList<Attempt>();
        long inputTokens = 0;
        long outputTokens = 0;
        long costMicros = 0;
        AiFailureCategory lastRetryable = null;

        for (var candidate : modelPolicy.candidates().stream().limit(modelPolicy.maxAttempts()).toList()) {
            var provider = providers.adapter(candidate.providerKey());
            if (provider == null) {
                attempts.add(new Attempt(candidate.candidateRef(), AiFailureCategory.PROVIDER_UNAVAILABLE));
                lastRetryable = AiFailureCategory.PROVIDER_UNAVAILABLE;
                if (!canFallback(lastRetryable, modelPolicy, attempts.size())) break;
                continue;
            }

            try {
                var result = provider.invoke(candidate, rendered, context, request.traceId());
                var cost = calculateCost(candidate, result.usage());
                inputTokens += result.usage().inputTokens();
                outputTokens += result.usage().outputTokens();
                costMicros += cost.amountMicros();

                enforceActualBudget(context, request, modelPolicy, result.usage(), cost);
                costLedger.record(
                    context.subjectUserId(),
                    request.purpose(),
                    request.requestId(),
                    candidate.candidateRef(),
                    result.usage(),
                    cost,
                    modelPolicy.budgetPolicy().windowKey()
                );

                if (!outputValid(prompt, result.text())) {
                    attempts.add(new Attempt(candidate.candidateRef(), AiFailureCategory.MALFORMED_OUTPUT));
                    lastRetryable = AiFailureCategory.MALFORMED_OUTPUT;
                    if (modelPolicy.fallbackOnMalformedOutput()
                        && canFallback(lastRetryable, modelPolicy, attempts.size())) {
                        continue;
                    }
                    fail(AiErrorCode.OUTPUT_VALIDATION_FAILED, "Provider output failed registered schema");
                }

                attempts.add(new Attempt(candidate.candidateRef(), null));
                var response = new Response(
                    result.text(),
                    candidate.candidateRef(),
                    prompt.version(),
                    modelPolicy.version(),
                    prompt.outputSchemaVersion(),
                    attempts.size() > 1 ? AiFallbackState.USED : AiFallbackState.NOT_USED,
                    modelPolicy.cachePolicy().enabled() && modelPolicy.cachePolicy().contentStorageAllowed()
                        ? AiCacheStatus.MISS : AiCacheStatus.DISABLED,
                    new TokenUsage(inputTokens, outputTokens),
                    Cost.usdMicros(costMicros),
                    List.copyOf(attempts)
                );

                writeCached(idempotencyKey, response, Duration.ofHours(24));
                if (modelPolicy.cachePolicy().enabled() && modelPolicy.cachePolicy().contentStorageAllowed()) {
                    writeCached(semanticKey, response, modelPolicy.cachePolicy().ttl());
                }
                return response;
            } catch (AiProviderException failure) {
                attempts.add(new Attempt(candidate.candidateRef(), failure.category()));
                lastRetryable = failure.category();
                if (!canFallback(failure.category(), modelPolicy, attempts.size())) {
                    var code = switch (failure.category()) {
                        case TIMEOUT -> AiErrorCode.PROVIDER_TIMEOUT;
                        case RATE_LIMIT -> AiErrorCode.RATE_LIMITED;
                        case PROVIDER_UNAVAILABLE, NON_RETRYABLE -> AiErrorCode.PROVIDER_UNAVAILABLE;
                        case MALFORMED_OUTPUT -> AiErrorCode.OUTPUT_VALIDATION_FAILED;
                    };
                    throw new AiGatewayException(code, "AI provider failed", failure);
                }
            }
        }

        throw new AiGatewayException(
            AiErrorCode.FALLBACK_EXHAUSTED,
            "AI fallback exhausted after " + attempts.size() + " attempts ("
                + (lastRetryable == null ? "unknown" : lastRetryable.name()) + ")"
        );
    }

    private String render(PromptDefinition prompt, Map<String,String> variables) {
        var safeVariables = variables == null ? Map.<String,String>of() : variables;
        var missing = new HashSet<>(prompt.requiredVariables());
        missing.removeAll(safeVariables.keySet());
        var unexpected = new HashSet<>(safeVariables.keySet());
        unexpected.removeAll(prompt.requiredVariables());
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            fail(AiErrorCode.INVALID_PROMPT_VARIABLES, "Prompt variables do not match registered schema");
        }

        var rendered = prompt.template();
        for (var key : prompt.requiredVariables().stream().sorted().toList()) {
            rendered = rendered.replace("{{" + key + "}}", safeVariables.get(key));
        }
        if (Pattern.compile("\\{\\{[a-zA-Z0-9_.-]+}}").matcher(rendered).find()) {
            fail(AiErrorCode.INVALID_PROMPT_VARIABLES, "Unresolved prompt variable");
        }
        return rendered;
    }

    private boolean outputValid(PromptDefinition prompt, String text) {
        return switch (prompt.outputSchemaKind()) {
            case NON_EMPTY_TEXT -> text != null && !text.isBlank();
            case SAFETY_CLASSIFICATION_V1 ->
                text != null && SAFETY_OUTPUT.matcher(text.trim()).matches();
        };
    }

    private void ensureBudget(ContextEnvelope context, Request request, ModelRoutingPolicy policy) {
        var spent = costLedger.spentMicros(
            context.subjectUserId(), request.purpose(), policy.budgetPolicy().windowKey()
        );
        if (spent >= policy.budgetPolicy().maxCostMicrosPerSubjectPurposeWindow()) {
            fail(AiErrorCode.BUDGET_EXCEEDED, "AI budget is exhausted");
        }
    }

    private void enforceActualBudget(
        ContextEnvelope context,
        Request request,
        ModelRoutingPolicy policy,
        TokenUsage usage,
        Cost cost
    ) {
        var budget = policy.budgetPolicy();
        if (usage.totalTokens() > budget.maxTotalTokensPerRequest()
            || cost.amountMicros() > budget.maxCostMicrosPerRequest()) {
            fail(AiErrorCode.BUDGET_EXCEEDED, "AI request exceeded registered budget");
        }
        var spent = costLedger.spentMicros(context.subjectUserId(), request.purpose(), budget.windowKey());
        if (spent + cost.amountMicros() > budget.maxCostMicrosPerSubjectPurposeWindow()) {
            fail(AiErrorCode.BUDGET_EXCEEDED, "AI subject-purpose budget would be exceeded");
        }
    }

    private Cost calculateCost(ModelCandidate candidate, TokenUsage usage) {
        var input = ((usage.inputTokens() * candidate.inputUsdMicrosPerThousandTokens()) + 999L) / 1000L;
        var output = ((usage.outputTokens() * candidate.outputUsdMicrosPerThousandTokens()) + 999L) / 1000L;
        return Cost.usdMicros(input + output);
    }

    private boolean canFallback(AiFailureCategory category, ModelRoutingPolicy policy, int attempts) {
        return policy.retryableCategories().contains(category) && attempts < policy.maxAttempts();
    }

    private Response readCached(CacheKey key, AiCacheStatus status) {
        try {
            var raw = cache.get(key);
            return raw == null ? null : decode(raw, status);
        } catch (RuntimeException failure) {
            throw new AiGatewayException(AiErrorCode.CACHE_FAILURE, "AI cache read failed", failure);
        }
    }

    private void writeCached(CacheKey key, Response response, Duration ttl) {
        try {
            cache.put(key, encode(response), ttl);
        } catch (RuntimeException failure) {
            throw new AiGatewayException(AiErrorCode.CACHE_FAILURE, "AI cache write failed", failure);
        }
    }

    private String fingerprint(
        Request request,
        ContextEnvelope context,
        PromptDefinition prompt,
        ModelRoutingPolicy policy,
        PolicyDecision decision
    ) {
        var variables = request.variables() == null ? Map.<String,String>of() : request.variables();
        var renderedVariables = variables.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(x -> x.getKey() + "=" + sha(x.getValue()))
            .reduce((a,b) -> a + "&" + b).orElse("");
        return sha(String.join("|",
            sha(context.subjectUserId()),
            request.purpose(),
            Integer.toString(context.purposeVersion()),
            context.snapshotId(),
            Long.toString(context.snapshotVersion()),
            context.fingerprint(),
            Integer.toString(context.contextSchemaVersion()),
            Integer.toString(context.outputSchemaVersion()),
            Integer.toString(context.projectionSchemaVersion()),
            context.projectionFingerprint(),
            prompt.key(),
            Integer.toString(prompt.version()),
            Integer.toString(prompt.outputSchemaVersion()),
            policy.key(),
            Integer.toString(policy.version()),
            decision.privacyPolicyVersion(),
            decision.safetyPolicyVersion(),
            renderedVariables
        ));
    }

    private String encode(Response response) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var attempts = response.attempts().stream().map(attempt ->
            encoder.encodeToString(attempt.candidateRef().getBytes(StandardCharsets.UTF_8))
                + ":" + (attempt.failureCategory() == null ? "OK" : attempt.failureCategory().name())
        ).reduce((a,b) -> a + "," + b).orElse("");
        return String.join("|",
            encoder.encodeToString(response.text().getBytes(StandardCharsets.UTF_8)),
            encoder.encodeToString(response.modelRef().getBytes(StandardCharsets.UTF_8)),
            Integer.toString(response.promptVersion()),
            Integer.toString(response.modelPolicyVersion()),
            Integer.toString(response.outputSchemaVersion()),
            response.fallbackState().name(),
            Long.toString(response.usage().inputTokens()),
            Long.toString(response.usage().outputTokens()),
            Long.toString(response.cost().amountMicros()),
            attempts
        );
    }

    private Response decode(String value, AiCacheStatus status) {
        var parts = value.split("\\|", -1);
        if (parts.length != 10) {
            throw new AiGatewayException(AiErrorCode.CACHE_FAILURE, "Invalid AI cache envelope");
        }
        var decoder = Base64.getUrlDecoder();
        var attempts = new ArrayList<Attempt>();
        if (!parts[9].isBlank()) {
            for (var item : parts[9].split(",")) {
                var pair = item.split(":", 2);
                attempts.add(new Attempt(
                    new String(decoder.decode(pair[0]), StandardCharsets.UTF_8),
                    pair.length < 2 || "OK".equals(pair[1]) ? null : AiFailureCategory.valueOf(pair[1])
                ));
            }
        }
        return new Response(
            new String(decoder.decode(parts[0]), StandardCharsets.UTF_8),
            new String(decoder.decode(parts[1]), StandardCharsets.UTF_8),
            Integer.parseInt(parts[2]),
            Integer.parseInt(parts[3]),
            Integer.parseInt(parts[4]),
            AiFallbackState.valueOf(parts[5]),
            status,
            new TokenUsage(Long.parseLong(parts[6]), Long.parseLong(parts[7])),
            Cost.usdMicros(Long.parseLong(parts[8])),
            List.copyOf(attempts)
        );
    }

    private String sha(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            var out = new StringBuilder(64);
            for (byte b : bytes) out.append("%02x".formatted(b & 0xff));
            return out.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private void validateRequest(Request request) {
        if (request == null || blank(request.purpose()) || blank(request.contextSnapshotId())
            || blank(request.promptKey()) || blank(request.modelPolicyKey())
            || blank(request.requestId()) || blank(request.idempotencyKey()) || blank(request.traceId())) {
            throw new IllegalArgumentException("Invalid AI request");
        }
    }

    private void validateModelPolicy(ModelRoutingPolicy policy) {
        if (policy.version() < 1 || policy.candidates() == null || policy.candidates().isEmpty()
            || policy.maxAttempts() < 1 || policy.maxAttempts() > policy.candidates().size()
            || policy.retryableCategories().contains(AiFailureCategory.NON_RETRYABLE)) {
            throw new IllegalArgumentException("Invalid AI model routing policy");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void fail(AiErrorCode code, String message) {
        throw new AiGatewayException(code, message);
    }
}
