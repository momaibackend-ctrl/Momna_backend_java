package com.momna.platform.safety;

public class SafetyGuardedAiException extends RuntimeException {
    private final String code;
    private final SafetyDecision decision;

    public SafetyGuardedAiException(String code, SafetyDecision decision) {
        super("AI invocation denied by canonical Safety decision: " + decision.route());
        this.code = code;
        this.decision = decision;
    }

    public String code() { return code; }
    public SafetyDecision decision() { return decision; }
}
