package com.momna.platform.safety;

import java.util.Set;

public record SafetyDecision(
    SafetyDisposition decision,
    String route,
    SafetySeverity severity,
    Set<String> allowedActions,
    String ruleVersion,
    String catalogVersion,
    String source,
    String fallbackState
) {
    public boolean blocked() { return decision == SafetyDisposition.BLOCK; }
}
