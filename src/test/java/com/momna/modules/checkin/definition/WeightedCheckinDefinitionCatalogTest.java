package com.momna.modules.checkin.definition;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.domain.CheckinDefinitionPeriod;
import com.momna.modules.checkin.domain.CheckinPhase;
import org.junit.jupiter.api.Test;

class WeightedCheckinDefinitionCatalogTest {
    @Test
    void preservesV3WhilePublishingV31ForAllFourteenDefinitions() {
        var catalog = new WeightedCheckinDefinitionCatalog(new ObjectMapper());
        assertEquals(14, catalog.all().size());
        assertEquals(102, catalog.all().stream().mapToInt(x -> x.items().size()).sum());
        assertTrue(catalog.all().stream().allMatch(x ->
            x.definitionVersion() == 4 && x.poolVersion().equals("weighted-v3.1")));
        int legacyCount = 0;
        for (var current : catalog.all()) {
            var v3 = catalog.get(current.period(), current.phase(), 3);
            assertEquals(3, v3.definitionVersion());
            assertEquals("weighted-v3", v3.poolVersion());
            assertEquals(current.items().size()
                - (current.period() == CheckinDefinitionPeriod.PREGNANCY
                    || (current.period() == CheckinDefinitionPeriod.POSTPARTUM
                        && current.phase() == CheckinPhase.MORNING) ? 1 : 0),
                v3.items().size());
            legacyCount += v3.items().size();
        }
        assertEquals(99, legacyCount);
    }

    @Test
    void p19AndPp19AreLocalizedAndDoNotInventProductWeights() {
        var catalog = new WeightedCheckinDefinitionCatalog(new ObjectMapper());
        for (var phase : CheckinPhase.values()) {
            var pregnancy = catalog.latest(CheckinDefinitionPeriod.PREGNANCY, phase);
            var p19 = pregnancy.items().stream().filter(x -> x.itemCode().equals("P19"))
                .findFirst().orElseThrow();
            assertEquals("Напряжение или схватки в животе", p19.labelRu());
            assertEquals("Tightening or contractions", p19.labelEnUs());
            assertEquals("regular or getting stronger", p19.states().get(2).enUs());
            assertEquals(0.0, p19.safetyWeight());
            assertFalse(p19.scored());
        }
        var morning = catalog.latest(CheckinDefinitionPeriod.POSTPARTUM, CheckinPhase.MORNING);
        var pp19 = morning.items().stream().filter(x -> x.itemCode().equals("PP19"))
            .findFirst().orElseThrow();
        assertEquals("Головная боль, зрение или отёки", pp19.labelRu());
        assertEquals("noticeable", pp19.states().get(2).enUs());
        assertTrue(catalog.latest(CheckinDefinitionPeriod.POSTPARTUM, CheckinPhase.EVENING)
            .items().stream().noneMatch(x -> x.itemCode().equals("PP19")));
        for (var definition : catalog.all()) {
            if (definition.period() == CheckinDefinitionPeriod.PREGNANCY
                || definition.period() == CheckinDefinitionPeriod.POSTPARTUM) continue;
            assertTrue(definition.items().stream()
                .noneMatch(x -> x.itemCode().equals("P19") || x.itemCode().equals("PP19")));
        }
    }
}
