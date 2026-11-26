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
    void postpartumVersionSixAsksTheMonthsSinceBirthAndAllowsASkip() {
        var postpartum = StandardFlowDefinitionCatalog.all().stream()
            .filter(x -> x.key().equals("period-onboarding-postpartum"))
            .findFirst()
            .orElseThrow();

        assertEquals(6, postpartum.version());

        var steps = (java.util.List<?>) postpartum.definitionJson().get("steps");
        var fallback = steps.stream()
            .map(x -> (java.util.Map<?, ?>) x)
            .filter(x -> "time_since_birth_approx".equals(x.get("stepId")))
            .findFirst()
            .orElseThrow();

        assertEquals(Boolean.TRUE, fallback.get("required"));
        assertEquals(Boolean.TRUE, fallback.get("skippable"), "the design's wheel may be skipped");
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

    /** MOMNA-1331 (26.11.2026): question 27 is never asked after the loss of a baby, including one of several. */
    @Test
    void theNextPregnancyQuestionIsNotAskedAfterALoss() {
        var spec = StandardFlowDefinitionCatalog.all().stream()
            .filter(x -> x.key().equals("period-onboarding-postpartum")).findFirst().orElseThrow();
        var model = com.momna.modules.flow.application.FlowDefinitionModel.from(
            new com.momna.modules.flow.infrastructure.FlowDefinitionEntity(spec.key(), spec.version(), spec.type(), null,
                spec.period(), null, spec.definitionJson(), 1, java.time.Instant.parse("2026-11-26T00:00:00Z")));
        var condition = model.conditions().get("onboarding.postpartum.next_pregnancy");
        java.util.function.Function<java.util.Map<String, java.util.Map<String, Object>>, Boolean> asked =
            values -> com.momna.modules.flow.application.FlowConditionEvaluator.matches(condition, values);
        assertTrue(asked.apply(java.util.Map.of("onboarding.postpartum.child_status",
            java.util.Map.of("kind", "ENUM", "value", "all_home"))));
        assertFalse(asked.apply(java.util.Map.of("onboarding.postpartum.child_status",
            java.util.Map.of("kind", "ENUM", "value", "loss"))));
        assertFalse(asked.apply(java.util.Map.of(
            "onboarding.postpartum.child_status", java.util.Map.of("kind", "ENUM", "value", "complicated"),
            "onboarding.postpartum.child_status_by_child",
            java.util.Map.of("kind", "OBJECT", "value", java.util.Map.of("1", "home", "2", "deceased")))),
            "a mixed outcome of twins");
        assertTrue(asked.apply(java.util.Map.of(
            "onboarding.postpartum.child_status", java.util.Map.of("kind", "ENUM", "value", "split_time"),
            "onboarding.postpartum.child_status_by_child",
            java.util.Map.of("kind", "OBJECT", "value", java.util.Map.of("1", "home", "2", "hospital")))));
    }

    /** Router v2 (design): "I recently gave birth" routes to postpartum without asking a time range. */
    @Test
    void routerVersionTwoSendsARecentBirthStraightToPostpartum() {
        var router = StandardFlowDefinitionCatalog.all().stream()
            .filter(x -> x.key().equals("lifecycle-router")).findFirst().orElseThrow();
        assertEquals(2, router.version());
        var fields = ((java.util.List<?>) router.definitionJson().get("steps")).stream()
            .map(x -> String.valueOf(((java.util.Map<?, ?>) x).get("fieldId"))).toList();
        assertFalse(fields.contains("onboarding.router.postpartum_time_range"));
        assertFalse(fields.contains("onboarding.router.postpartum_main_need"));
        var decision = com.momna.modules.flow.application.RouterRoutingTable.route(java.util.Map.of(
            "onboarding.router.current_situation", "recent_birth"));
        assertEquals(com.momna.modules.lifecycle.domain.LifecyclePeriod.POSTPARTUM, decision.period());
    }

    /** Router v1 only. MOMNA-842 over MOMNA-990: more than a year after birth, recovery is a context of the cycle period. */
    @Test
    void recoveryMoreThanAYearAfterBirthIsACycleWithARecoveryContext() {
        var decision = com.momna.modules.flow.application.RouterRoutingTable.route(java.util.Map.of(
            "onboarding.router.current_situation", "recent_birth",
            "onboarding.router.postpartum_time_range", "more_than_year",
            "onboarding.router.postpartum_main_need", "recovery"));
        assertEquals(com.momna.modules.lifecycle.domain.LifecyclePeriod.CYCLE, decision.period());
        assertTrue(decision.additionalContexts().contains(
            com.momna.modules.flow.application.RouterRoutingTable.POSTPARTUM_RECOVERY));
        var firstYear = com.momna.modules.flow.application.RouterRoutingTable.route(java.util.Map.of(
            "onboarding.router.current_situation", "recent_birth",
            "onboarding.router.postpartum_time_range", "m10_12"));
        assertEquals(com.momna.modules.lifecycle.domain.LifecyclePeriod.POSTPARTUM, firstYear.period());
    }

    /** The months since birth are asked only without a birth date. */
    @Test
    void theTimeSinceBirthIsAskedOnlyWhenNothingElseTellsIt() {
        var spec = StandardFlowDefinitionCatalog.all().stream()
            .filter(x -> x.key().equals("period-onboarding-postpartum")).findFirst().orElseThrow();
        var model = com.momna.modules.flow.application.FlowDefinitionModel.from(
            new com.momna.modules.flow.infrastructure.FlowDefinitionEntity(spec.key(), spec.version(), spec.type(), null,
                spec.period(), null, spec.definitionJson(), 1, java.time.Instant.parse("2026-11-26T00:00:00Z")));
        var condition = model.conditions().get("onboarding.postpartum.time_since_birth_approx");
        java.util.function.Function<java.util.Map<String, java.util.Map<String, Object>>, Boolean> asked =
            values -> com.momna.modules.flow.application.FlowConditionEvaluator.matches(condition, values);
        assertTrue(asked.apply(java.util.Map.of()), "no birth date");
        assertFalse(asked.apply(java.util.Map.of("onboarding.postpartum.birth_date",
            java.util.Map.of("kind", "DATE", "value", "2026-10-01"))));
    }
}
