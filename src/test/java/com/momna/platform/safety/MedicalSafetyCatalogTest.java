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
}
