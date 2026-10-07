package com.momna.core.context.application;

public interface ContextAccessGuard {
    boolean allowed(String userId, String sensitivityScope, String purpose, boolean consentRequired);
}
