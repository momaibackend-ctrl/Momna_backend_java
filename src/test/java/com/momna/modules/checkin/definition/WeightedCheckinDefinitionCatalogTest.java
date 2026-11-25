package com.momna.modules.checkin.definition;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.domain.CheckinDefinitionPeriod;
import com.momna.modules.checkin.domain.CheckinPhase;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeightedCheckinDefinitionCatalogTest {
    private final WeightedCheckinDefinitionCatalog catalog = new WeightedCheckinDefinitionCatalog(new ObjectMapper());

    /** weighted-v3.1 (MOMNA-1145 D13): the v3 pool plus P19 (pregnancy, morning and evening) and PP19. */
    @Test
    void theCurrentPoolIsWeightedV31WithP19AndPp19() {
        assertEquals(14, catalog.all().size());
        assertEquals(102, catalog.all().stream().mapToInt(x -> x.items().size()).sum());
        assertTrue(catalog.all().stream().allMatch(x -> x.definitionVersion() == 4));
        assertTrue(catalog.all().stream().allMatch(x -> x.poolVersion().equals("weighted-v3.1")));
        assertEquals(List.of(true, true, true), List.of(
            has(CheckinDefinitionPeriod.PREGNANCY, CheckinPhase.MORNING, "P19"),
            has(CheckinDefinitionPeriod.PREGNANCY, CheckinPhase.EVENING, "P19"),
            has(CheckinDefinitionPeriod.POSTPARTUM, CheckinPhase.MORNING, "PP19")));
        assertFalse(has(CheckinDefinitionPeriod.POSTPARTUM, CheckinPhase.EVENING, "PP19"));

        var p19 = item(CheckinDefinitionPeriod.PREGNANCY, CheckinPhase.MORNING, "P19");
        assertEquals("Напряжение или схватки в животе", p19.labelRu());
        assertEquals("Tightening or contractions", p19.labelEnUs());
        assertEquals("регулярно или усиливаются", p19.states().get(2).ru());
        assertEquals("regular or getting stronger", p19.states().get(2).enUs());
        assertFalse(p19.scored(), "no product-approved weights: unscored, the direct entry still works");
        assertEquals(0.0, p19.safetyWeight());
        var pp19 = item(CheckinDefinitionPeriod.POSTPARTUM, CheckinPhase.MORNING, "PP19");
        assertEquals("Headache, vision or swelling", pp19.labelEnUs());
        assertEquals(List.of("нет", "лёгкие", "заметные"), pp19.states().stream().map(s -> s.ru()).toList());
    }

    /** Sessions started on weighted-v3 keep reading their own definition. */
    @Test
    void v3SessionsStillReadTheirDefinition() {
        for (var current : catalog.all()) {
            var v3 = catalog.get(current.period(), current.phase(), 3);
            assertEquals("weighted-v3", v3.poolVersion());
            assertTrue(v3.items().stream().noneMatch(i -> i.itemCode().equals("P19") || i.itemCode().equals("PP19")));
            assertEquals(current.items().size() - v3.items().size(),
                current.items().stream().filter(i -> i.itemCode().endsWith("19") && !i.scored()).count());
        }
        assertThrows(IllegalStateException.class,
            () -> catalog.get(CheckinDefinitionPeriod.CYCLE, CheckinPhase.MORNING, 2));
    }

    private boolean has(CheckinDefinitionPeriod period, CheckinPhase phase, String code) {
        return catalog.latest(period, phase).items().stream().anyMatch(i -> i.itemCode().equals(code));
    }

    private WeightedCheckinDefinitionCatalog.Item item(CheckinDefinitionPeriod period, CheckinPhase phase, String code) {
        return catalog.latest(period, phase).items().stream().filter(i -> i.itemCode().equals(code)).findFirst()
            .orElseThrow();
    }
}
