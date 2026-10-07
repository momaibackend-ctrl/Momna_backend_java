package com.momna.modules.checkin.scoring;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog;
import com.momna.modules.checkin.domain.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeightedCheckinScoringEngineTest {
    @Test
    void preservesExplicitZeroAsAnsweredWithZeroContribution() {
        var catalog = new WeightedCheckinDefinitionCatalog(new ObjectMapper());
        var engine = new WeightedCheckinScoringEngine(catalog);
        var definition = catalog.latest(CheckinDefinitionPeriod.CYCLE, CheckinPhase.MORNING);
        var item = definition.items().stream().filter(x -> x.scored()).findFirst().orElseThrow();

        var result = engine.score(
            CheckinDefinitionPeriod.CYCLE,
            CheckinPhase.MORNING,
            definition.definitionVersion(),
            Set.of(item.itemCode()),
            Map.of(item.itemCode(), 0),
            Map.of()
        );

        assertEquals(1, result.answeredItemCount());
        assertEquals(1, result.scoredAnsweredItemCount());
        assertEquals(new BigDecimal("0.000000"), result.adjustmentScore());
        assertEquals(new BigDecimal("0.000000"), result.safetyScore());
    }

    @Test
    void rejectsValuesOutsideZeroOneTwo() {
        var catalog = new WeightedCheckinDefinitionCatalog(new ObjectMapper());
        var engine = new WeightedCheckinScoringEngine(catalog);
        var definition = catalog.latest(CheckinDefinitionPeriod.CYCLE, CheckinPhase.MORNING);
        var item = definition.items().getFirst();

        assertThrows(IllegalArgumentException.class, () -> engine.score(
            CheckinDefinitionPeriod.CYCLE,
            CheckinPhase.MORNING,
            definition.definitionVersion(),
            Set.of(item.itemCode()),
            Map.of(item.itemCode(), 3),
            Map.of()
        ));
    }
}
