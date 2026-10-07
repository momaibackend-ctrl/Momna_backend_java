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
    void preservesPostpartumVersionTwoAndMandatoryFallbackTimeAnchor() {
        var postpartum = StandardFlowDefinitionCatalog.all().stream()
            .filter(x -> x.key().equals("period-onboarding-postpartum"))
            .findFirst()
            .orElseThrow();

        assertEquals(2, postpartum.version());

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
}
