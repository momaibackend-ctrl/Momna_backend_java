package com.momna.platform.safety;

import com.momna.platform.ai.AiContracts;
import com.momna.platform.ai.AiPorts;
import java.util.Set;

public class CanonicalSafetyGuardedAi {
    private final CanonicalSafetyService safety;
    private final AiPorts.Gateway ai;

    public CanonicalSafetyGuardedAi(
        CanonicalSafetyService safety,
        AiPorts.Gateway ai
    ) {
        this.safety = safety;
        this.ai = ai;
    }

    public Response evaluateAndExecute(Request request) {
        if (request == null || request.ai() == null) {
            throw new IllegalArgumentException("Safety-guarded AI request is required");
        }
        if (request.traceId() == null || !request.traceId().equals(request.ai().traceId())) {
            throw new IllegalArgumentException("Safety and AI traceId must match");
        }
        if (request.contextSnapshotId() != null
            && !request.contextSnapshotId().equals(request.ai().contextSnapshotId())) {
            throw new IllegalArgumentException("Safety and AI context snapshot must match");
        }

        var decision = safety.evaluate(
            request.signalKeys() == null ? Set.of() : request.signalKeys()
        );
        if (decision.decision() == SafetyDisposition.REDIRECT) {
            throw new SafetyGuardedAiException("SAFETY_REDIRECT", decision);
        }
        if (decision.decision() == SafetyDisposition.BLOCK) {
            throw new SafetyGuardedAiException("SAFETY_BLOCKED", decision);
        }
        return new Response(decision, ai.execute(request.ai()));
    }

    public record Request(
        Set<String> signalKeys,
        String traceId,
        String contextSnapshotId,
        AiContracts.Request ai
    ) {}

    public record Response(
        SafetyDecision safety,
        AiContracts.Response ai
    ) {}
}
