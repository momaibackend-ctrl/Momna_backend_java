package com.momna.modules.flow.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StandardFlowDefinitionCatalogTest {
    @Test
    void containsCanonicalRouterAndAllSevenPeriodOnboardingDefinitions() {
        var definitions = StandardFlowDefinitionCatalog.all();
        assertEquals(8, definitions.size());

        var keys = definitions.stream()
            .map(StandardFlowDefinitionCatalog.Spec::key)
            .collect(Collectors.toSet());

        assertEquals(Set.of(
            "lifecycle-router",
            "period-onboarding-menarche",
            "period-onboarding-cycle",
            "period-onboarding-planning",
            "period-onboarding-pregnancy",
            "period-onboarding-postpartum",
            "period-onboarding-perimenopause",
            "period-onboarding-menopause"
        ), keys);
    }

    @Test
    void postpartumVersionThreeKeepsTheMandatoryFallbackTimeAnchor() {
        var postpartum = StandardFlowDefinitionCatalog.all().stream()
            .filter(x -> x.key().equals("period-onboarding-postpartum"))
            .findFirst()
            .orElseThrow();

        assertEquals(3, postpartum.version());

        var steps = (java.util.List<?>) postpartum.definitionJson().get("steps");
        var fallback = steps.stream()
            .map(x -> (java.util.Map<?, ?>) x)
            .filter(x -> "time_since_birth_approx".equals(x.get("stepId")))
            .findFirst()
            .orElseThrow();

        assertEquals(Boolean.TRUE, fallback.get("required"));
        assertEquals(Boolean.FALSE, fallback.get("skippable"));
        assertNotNull(fallback.get("condition"));
    }

    /**
     * MOMNA-561 (24.11.2026): "Is the baby currently receiving breast milk, including pumped?" follows the
     * feeding question and is asked only after tube/medically supported feeding or a changing plan.
     */
    @Test
    void theBreastMilkClarificationFollowsOnlyTheTwoUnclearFeedingAnswers() {
        var spec = StandardFlowDefinitionCatalog.all().stream()
            .filter(x -> x.key().equals("period-onboarding-postpartum")).findFirst().orElseThrow();
        var model = com.momna.modules.flow.application.FlowDefinitionModel.from(
            new com.momna.modules.flow.infrastructure.FlowDefinitionEntity(spec.key(), spec.version(), spec.type(), null,
                spec.period(), null, spec.definitionJson(), 1, java.time.Instant.parse("2026-11-24T00:00:00Z")));
        var ids = model.steps().stream().map(s -> s.stepId()).toList();
        assertEquals(ids.indexOf("feeding") + 1, ids.indexOf("feeding_breast_milk_now"));
        var step = model.steps().get(ids.indexOf("feeding_breast_milk_now"));
        assertFalse(step.required());
        assertTrue(step.skippable(), "a skipped answer is UNKNOWN");
        var condition = model.conditions().get("onboarding.postpartum.feeding_breast_milk_now");
        for (var code : java.util.List.of("nursing_at_breast", "pumped_breast_milk", "combination_feeding",
            "infant_formula", "tube_or_medically_supported", "feeding_plan_changing", "prefer_not_to_say")) {
            boolean asked = code.equals("tube_or_medically_supported") || code.equals("feeding_plan_changing");
            var oneBaby = java.util.Map.<String, java.util.Map<String, Object>>of("onboarding.postpartum.feeding",
                java.util.Map.of("value", java.util.Map.of("1", code)));
            var scalar = java.util.Map.<String, java.util.Map<String, Object>>of("onboarding.postpartum.feeding",
                java.util.Map.of("value", code));
            assertEquals(asked, com.momna.modules.flow.application.FlowConditionEvaluator.matches(condition, oneBaby), code);
            assertEquals(asked, com.momna.modules.flow.application.FlowConditionEvaluator.matches(condition, scalar), code);
        }
        var twins = java.util.Map.<String, java.util.Map<String, Object>>of("onboarding.postpartum.feeding",
            java.util.Map.of("value", java.util.Map.of("1", "infant_formula", "2", "feeding_plan_changing")));
        assertTrue(com.momna.modules.flow.application.FlowConditionEvaluator.matches(condition, twins));
    }
}
