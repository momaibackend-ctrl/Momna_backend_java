package com.momna.core.privacy;

public class PrivacyPolicyException extends IllegalStateException {
    private final PrivacyPolicyService.Decision decision;

    public PrivacyPolicyException(PrivacyPolicyService.Decision decision) {
        super("Privacy policy denied operation: " + decision.code());
        this.decision = decision;
    }

    public PrivacyPolicyService.Decision decision() { return decision; }
}
