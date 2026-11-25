package com.momna.modules.checkin.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog;
import com.momna.modules.checkin.domain.CheckinDefinitionPeriod;
import com.momna.modules.checkin.domain.CheckinSafetyState;
import com.momna.platform.safety.MedicalSafetyCatalog;
import com.momna.platform.safety.MedicalStep;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckinMedicalSafetyTest {
    private static MedicalStep complete(String routeId, String code, String disposition, String action) {
        return new MedicalStep(routeId, code, "medical-safety-v2.2", "medical-safety-rules-v2.2", "COMPLETE",
            disposition, "S", action, null, "CLINICAL_RELEASE_PENDING");
    }

    private static MedicalStep question(String routeId, String code) {
        return new MedicalStep(routeId, code, "medical-safety-v2.2", "medical-safety-rules-v2.2", "QUESTION",
            "PENDING", "Q", "ANSWER_FACT", "F_X", "CLINICAL_RELEASE_PENDING");
    }

    /** Every route of a period is bound to items that period's current Check-in really asks. */
    @Test
    void everyRouteBindingIsAnItemOfItsPeriod() {
        var safety = new MedicalSafetyCatalog(new ObjectMapper());
        var definitions = new WeightedCheckinDefinitionCatalog(new ObjectMapper());
        for (var route : safety.routes()) {
            if (route.period().equals("GLOBAL")) continue;
            var period = CheckinDefinitionPeriod.valueOf(route.period());
            for (var item : route.itemCodes()) {
                boolean asked = definitions.all().stream().filter(d -> d.period() == period)
                    .anyMatch(d -> d.items().stream().anyMatch(i -> i.itemCode().equals(item)));
                assertTrue(asked, route.routeId() + " is bound to " + item + ", which " + period + " never asks");
            }
        }
    }

    @Test
    void severityFollowsTheDisposition() {
        assertEquals("CRITICAL", CheckinMedicalSafety.severity(complete("R1", "C", "EMERGENCY", "CALL_EMERGENCY")));
        assertEquals("CRITICAL", CheckinMedicalSafety.severity(complete("R18", "C", "SAFETY_ASSESSMENT", "ACKNOWLEDGE")));
        assertEquals("HIGH", CheckinMedicalSafety.severity(complete("R1", "C", "URGENT", "ACKNOWLEDGE")));
        assertEquals("MEDIUM", CheckinMedicalSafety.severity(complete("R1", "C", "PROMPT", "ACKNOWLEDGE")));
        assertEquals("UNKNOWN", CheckinMedicalSafety.severity(complete("R1", "C", "UNKNOWN", "REVIEW_ANSWER")));
        assertEquals("LOW", CheckinMedicalSafety.severity(complete("R23", "C", "ROUTINE", "ACKNOWLEDGE")));
        assertEquals("NONE", CheckinMedicalSafety.severity(complete("R1", "C", "NOT_REPORTED", "OPEN_ER0")));
        assertEquals("UNKNOWN", CheckinMedicalSafety.severity(question("R1", "C")));
    }

    @Test
    void theSessionDecisionIsItsStrongestRouteAndAnOpenQuestionBlocks() {
        var none = CheckinMedicalSafety.projection(List.of());
        assertEquals(CheckinSafetyState.CLEAR, none.state());
        assertNull(none.routeCode());
        assertFalse(none.blocking());

        var resolved = CheckinMedicalSafety.projection(List.of(
            complete("R11", "POSTPARTUM_PAIN", "PROMPT", "ACKNOWLEDGE"),
            complete("R12", "POSTPARTUM_BLEEDING", "URGENT", "ACKNOWLEDGE"),
            complete("R13", "POSTPARTUM_WOUND", "NOT_REPORTED", "OPEN_ER0")));
        assertEquals(CheckinSafetyState.RESOLVED, resolved.state());
        assertEquals("POSTPARTUM_BLEEDING", resolved.routeCode());
        assertEquals("HIGH", resolved.severity());
        assertEquals("medical-safety-v2.2:medical-safety-rules-v2.2", resolved.decisionVersion());
        assertEquals(List.of("ACKNOWLEDGE"), resolved.allowedActions());
        assertEquals(List.of("R11", "R12", "R13"), resolved.clarification().get("routeIds"));
        assertFalse(resolved.blocking());

        var open = CheckinMedicalSafety.projection(List.of(
            complete("R11", "POSTPARTUM_PAIN", "NOT_REPORTED", "OPEN_ER0"), question("R12", "POSTPARTUM_BLEEDING")));
        assertEquals(CheckinSafetyState.CLARIFICATION_REQUIRED, open.state());
        assertTrue(open.blocking());
        assertEquals(List.of(CheckinMedicalSafety.ANSWER_SAFETY), open.allowedActions());
        assertEquals("UNKNOWN", open.severity());

        var tie = CheckinMedicalSafety.projection(List.of(
            complete("R5", "PREGNANCY_SWELLING", "URGENT", "ACKNOWLEDGE"),
            complete("R7", "PREGNANCY_HEADACHE", "URGENT", "ACKNOWLEDGE")));
        assertEquals("PREGNANCY_SWELLING", tie.routeCode(), "a tie keeps the first route");
    }
}
