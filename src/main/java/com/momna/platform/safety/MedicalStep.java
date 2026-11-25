package com.momna.platform.safety;

import java.util.List;

/**
 * One route's current step: a question to ask (state QUESTION, nextFactCode or a QM checklist) or the outcome
 * (state COMPLETE with a disposition, the screen to show and the action to offer).
 */
public record MedicalStep(String routeId, String routeCode, String catalogVersion,
    String ruleVersion, String state, String disposition, String screenCode,
    String actionCode, String nextFactCode, String conditionStatus,
    List<String> questionFactCodes) {
    public MedicalStep(String routeId, String routeCode, String catalogVersion,
        String ruleVersion, String state, String disposition, String screenCode,
        String actionCode, String nextFactCode, String conditionStatus) {
        this(routeId, routeCode, catalogVersion, ruleVersion, state, disposition,
            screenCode, actionCode, nextFactCode, conditionStatus, List.of());
    }

    public MedicalStep {
        questionFactCodes = List.copyOf(questionFactCodes);
    }

    public boolean question() { return "QUESTION".equals(state); }

    /**
     * The platform severity of this step, which My Day reads: EMERGENCY and the PM2 safety assessment are
     * CRITICAL, URGENT is HIGH, PROMPT is MEDIUM, UNKNOWN and a question still open are UNKNOWN, ROUTINE is
     * LOW, nothing found is NONE.
     */
    public String severity() {
        if (question()) return "UNKNOWN";
        return switch (disposition) {
            case "EMERGENCY", "SAFETY_ASSESSMENT" -> "CRITICAL";
            case "URGENT" -> "HIGH";
            case "PROMPT" -> "MEDIUM";
            case "ROUTINE" -> "LOW";
            case "NOT_REPORTED" -> "NONE";
            default -> "UNKNOWN";
        };
    }
}
