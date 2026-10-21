package com.momna.platform.safety;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class MedicalSafetyCatalogTest {
    private final MedicalSafetyCatalog catalog = new MedicalSafetyCatalog(new ObjectMapper());

    @Test
    void allTwentyEightRoutesHaveResolvedEntryAndFacts() {
        assertEquals(28, catalog.routes().size());
        assertEquals(53, catalog.facts().size()); // 47 question facts + 6 derived
        for (var route : catalog.routes()) {
            if (route.routeId().equals("R25") || route.routeId().equals("R26")) {
                assertEquals("GLOBAL", route.period());
                continue; // ER0 entry; no daily check-in item
            }
            assertFalse(route.itemCodes().isEmpty(), route.routeId());
            for (var code : route.itemCodes()) {
                int entryState = code.equals("PP2") ? 1 : 2;
                assertTrue(catalog.applicable(route.period(), code, entryState)
                    .contains(route), code + " -> " + route.routeId());
            }
            for (var fact : route.questions()) assertNotNull(catalog.fact(fact));
        }
    }

    @Test
    void zeroWeightedItemsReachR10R17AndR28WithoutScoring() {
        assertEquals("R10", catalog.applicable("PREGNANCY", "P19", 2).getFirst().routeId());
        assertEquals("R10", catalog.applicable("PREGNANCY", "P5", 2).getFirst().routeId());
        assertEquals("R17", catalog.applicable("POSTPARTUM", "PP19", 2).getFirst().routeId());
        assertEquals("R28", catalog.applicable("PLANNING", "T5", 2).getFirst().routeId());
        assertTrue(catalog.applicable("MENOPAUSE", "P19", 2).isEmpty());
        assertTrue(catalog.applicable("POSTPARTUM", "PP19", 1).isEmpty());
    }

    @Test
    void pp2StateTwoIsNotEmotionallyHard() {
        assertEquals("R18", catalog.applicable("POSTPARTUM", "PP2", 1)
            .getFirst().routeId());
        assertTrue(catalog.applicable("POSTPARTUM", "PP2", 2).isEmpty());
        assertEquals("F_HARM_THOUGHTS", catalog.route("R18").questions().getFirst());
        assertEquals(336, catalog.route("R18").cooldownHours());
        assertEquals(0, catalog.route("R25").cooldownHours());
    }
    @Test
    void endUserCatalogNeverContainsDraftAnnotationsOrBothHarmQuestions() throws Exception {
        var mapper = new ObjectMapper();
        var postpartum = mapper.writeValueAsString(catalog.publicFacts("POSTPARTUM"));
        var adult = mapper.writeValueAsString(catalog.publicFacts("CYCLE"));
        var routes = mapper.writeValueAsString(catalog.publicRoutes());
        var screens = mapper.writeValueAsString(catalog.publicScreens("POSTPARTUM"));
        assertTrue(postpartum.contains("Были мысли причинить вред себе или ребёнку?"));
        assertFalse(postpartum.contains("Другие периоды:"));
        assertFalse(postpartum.contains("Other stages:"));
        assertFalse(postpartum.contains("§5.7"));
        assertFalse(postpartum.contains("rule"));
        assertTrue(adult.contains("Были мысли причинить вред себе или другому человеку?"));
        assertFalse(adult.contains("Postpartum:"));
        assertFalse(routes.contains("новый"));
        assertTrue(routes.contains("PREGNANCY_NAUSEA"));
        assertFalse(routes.contains("crossChecks"));
        assertTrue(screens.contains("Как вы чувствуете себя эмоционально"));
        assertFalse(screens.contains("localizedSource"));
        assertFalse(screens.contains("postpartumBody"));
        assertFalse(screens.contains("Календарь (§5.7)"));
        assertEquals(22, catalog.publicScreens("POSTPARTUM").path("screens").size());
        assertEquals(1, catalog.questionGroups().size());
    }

    @Test
    void safetyTriageIsDrivenByVersionedStructuredPolicy() {
        assertTrue(catalog.conditionUndefinedFacts().contains("F_HEART_RACING"));
        assertTrue(catalog.postpartumBleedingEmergencyFacts().contains("F_PAD_SOAK_1H"));
        assertTrue(catalog.postpartumBleedingEmergencyFacts().contains("F_PRESYNCOPE"));
        assertEquals("R12", catalog.postpartumBleedingRoute());
        assertTrue(catalog.postpartumInfectionRoutes().contains("R15"));
        assertEquals(5, catalog.systemicChecklistFacts().size());
    }

}
