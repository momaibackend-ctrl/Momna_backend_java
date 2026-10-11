package com.momna.modules.checkin.safety;

import com.momna.modules.checkin.scoring.CheckinScoringResult;
import com.momna.modules.checkin.scoring.CheckinAdjustmentTier;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenarcheSafetyRouteSelectorTest {
    private CheckinScoringResult.Contribution item(String code, String contribution) {
        return new CheckinScoringResult.Contribution(
            code, 2, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ONE, BigDecimal.ONE, new BigDecimal(contribution), List.of()
        );
    }

    private CheckinScoringResult result(boolean threshold,
        CheckinScoringResult.Contribution... items) {
        return new CheckinScoringResult(BigDecimal.ZERO, new BigDecimal("12"),
            CheckinAdjustmentTier.NONE, threshold, items.length, items.length,
            List.of(items));
    }

    @Test void selectsLargestMenarcheSafetyContribution() {
        var result=result(true,item("M3","2"),item("M4","7"),item("M5","4"));
        assertEquals("MENARCHE_DIZZINESS",
            new MenarcheSafetyRouteSelector().select(result).orElseThrow());
    }

    @Test void equalContributionsRespectDefinitionOrder() {
        var result=result(true,item("M5","5"),item("M4","5"),item("M3","5"));
        assertEquals("MENARCHE_BLEEDING",
            new MenarcheSafetyRouteSelector().select(result).orElseThrow());
    }

    @Test void noThresholdDoesNotOpenSafetyFlow() {
        assertTrue(new MenarcheSafetyRouteSelector()
            .select(result(false,item("M5","20"))).isEmpty());
    }

    @Test void zeroOrUnboundContributionsDoNotTriggerRoute() {
        assertTrue(new MenarcheSafetyRouteSelector()
            .select(result(true,item("M3","0"),item("C4","8"))).isEmpty());
    }
}
