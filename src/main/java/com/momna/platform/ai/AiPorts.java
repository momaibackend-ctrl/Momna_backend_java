package com.momna.platform.ai;

import com.momna.platform.ai.AiContracts.*;

public final class AiPorts {
    private AiPorts() {}

    public interface ContextAccess {
        ContextEnvelope resolve(Request request);
    }

    public interface InvocationPolicyGuard {
        PolicyDecision authorize(Request request, ContextEnvelope context);
    }

    public interface PromptRegistry {
        PromptDefinition resolve(String key, Integer version);
    }

    public interface ModelRegistry {
        ModelRoutingPolicy resolvePolicy(String key, Integer version);
    }

    public interface ProviderAdapter {
        String providerKey();
        ProviderResult invoke(ModelCandidate candidate, String renderedPrompt, ContextEnvelope context, String traceId);
    }

    public interface ProviderRegistry {
        ProviderAdapter adapter(String providerKey);
    }

    public interface CostLedger {
        long spentMicros(String subjectUserId, String purpose, String windowKey);
        void record(String subjectUserId, String purpose, String requestId, String candidateRef, TokenUsage usage, Cost cost, String windowKey);
    }

    public interface Gateway {
        Response execute(Request request);
    }
}
