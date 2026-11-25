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
}
