package com.momna.platform.safety;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class MedicalSafetyRouteStateTest {
    private static final Instant T0 = Instant.parse("2026-10-20T10:00:00Z");

    private MedicalStep complete(String disposition, String screen) {
        return new MedicalStep(
            "R10", "PREGNANCY_PRETERM_LABOR",
            MedicalSafetyCatalog.VERSION, MedicalSafetyCatalog.RULE_VERSION,
            "COMPLETE", disposition, screen, "ACKNOWLEDGE", null,
            "CLINICAL_RELEASE_PENDING"
        );
    }

    @Test
    void appliesConfiguredCooldownAndAllowsLaterReentry() {
        for (int hours : new int[] {24,72,168,336}) {
            var record = new MedicalSafetyRouteStateEntity("user", "R10", T0);
            record.begin("day-1", "P19", T0);
            record.answer("F_FLUID_LEAK", "NO", T0);
            record.update(complete("NOT_REPORTED", "H1"), T0, hours);
            assertFalse(record.mayStart("P19", "day-2", T0.plusSeconds(hours * 3600L - 1)),
                "Cooldown incorrectly lifted at " + hours);
            assertTrue(record.mayStart("P19", "day-2", T0.plusSeconds(hours * 3600L)),
                "Cooldown should expire at " + hours);
        }
    }

    @Test
    void renewedSymptomAndDifferentItemBypassCooldown() {
        var record = new MedicalSafetyRouteStateEntity("user", "R10", T0);
        record.begin("day-1", "P19", T0);
        record.answer("F_FLUID_LEAK", "NO", T0);
        record.update(complete("NOT_REPORTED", "H1"), T0, 72);
        assertTrue(record.mayStart("P5", "day-2", T0.plusSeconds(100)));
        record.observeRecovery("P19", T0.plusSeconds(110));
        assertTrue(record.getResetObserved());
        assertTrue(record.mayStart("P19", "day-2", T0.plusSeconds(120)));
    }

    /** L13.5: a YES warning is never re-asked inside its window, whatever item comes; after it, a fresh run. */
    @Test
    void aYesWarningHoldsForItsWindowThenTheRouteStartsAfresh() {
        var record = new MedicalSafetyRouteStateEntity("user", "R10", T0);
        record.begin("day-1", "P19", T0);
        record.answer("F_FLUID_LEAK", "YES", T0);
        record.update(complete("URGENT", "OB1"), T0, 72);
        assertEquals(T0.plus(Duration.ofHours(72)), record.getCooldownUntil());
        assertFalse(record.mayStart("P19", "day-2", T0.plus(Duration.ofHours(71))));
        assertFalse(record.mayStart("P5", "day-2", T0.plus(Duration.ofHours(71))), "a new item does not reopen a YES");
        record.observeRecovery("P19", T0.plus(Duration.ofHours(1)));
        assertFalse(record.mayStart("P19", "day-2", T0.plus(Duration.ofHours(2))), "nor does a worsening");
        assertEquals("YES", record.getFactAnswers().get("F_FLUID_LEAK"));
        assertTrue(record.mayStart("P19", "day-4", T0.plus(Duration.ofHours(72))));
        record.begin("day-4", "P19", T0.plus(Duration.ofHours(72)));
        assertTrue(record.getFactAnswers().isEmpty(), "a fresh run asks again");
    }

    @Test
    void aSessionNeverRunsTheSameRouteTwice() {
        var record = new MedicalSafetyRouteStateEntity("user", "R10", T0);
        record.begin("day-1", "P19", T0);
        record.answer("F_FLUID_LEAK", "NO", T0);
        record.update(complete("NOT_REPORTED", "H1"), T0, 72);
        record.observeRecovery("P19", T0.plusSeconds(5));
        assertFalse(record.mayStart("P19", "day-1", T0.plusSeconds(10)));
        assertFalse(record.mayStart("P5", "day-1", T0.plusSeconds(10)));
        assertTrue(record.mayStart("P19", "day-2", T0.plusSeconds(10)), "a worsening in a later session bypasses");
    }

    @Test
    void anOpenQuestionOfAnEarlierSessionMovesOnWithItsAnswers() {
        var record = new MedicalSafetyRouteStateEntity("user", "R10", T0);
        record.begin("morning", "P19", T0);
        record.answer("F_FLUID_LEAK", "NO", T0);
        assertTrue(record.mayStart("P19", "evening", T0.plusSeconds(60)));
        record.begin("evening", "P19", T0.plusSeconds(60));
        assertEquals("NO", record.getFactAnswers().get("F_FLUID_LEAK"));
        assertEquals("evening", record.getSessionId());
    }

    @Test
    void skipsCooldownForEmergencyEntryButNotForOrdinaryDailyCheck() {
        var record = new MedicalSafetyRouteStateEntity("user", "R25", T0);
        record.begin(null, "ER0:ALLERGY_SWELLING", T0);
        record.answer("F_AIRWAY_SWELLING", "NO", T0);
        record.update(complete("NOT_REPORTED", "H1"), T0, 0);
        assertNull(record.getCooldownUntil());
        assertTrue(record.mayStart("ER0:ALLERGY_SWELLING", null, T0.plusSeconds(1)));
    }

    @Test
    void ignoresSameSessionDuplicateActiveStart() {
        var record = new MedicalSafetyRouteStateEntity("user", "R10", T0);
        record.begin("same-session", "P19", T0);
        assertFalse(record.mayStart("P19", "same-session", T0.plusSeconds(1)));
        assertTrue(record.mayStart("P19", "new-session", T0.plusSeconds(1)));
    }

    @Test
    void acceptsMeasuredBloodPressureOrExplicitUnknownWithoutInventingThresholds() {
        var record = new MedicalSafetyRouteStateEntity("user", "R17", T0);
        record.recordBloodPressure(null, null, true, T0);
        assertEquals("UNKNOWN", record.getFactAnswers().get("BP_READING"));
        record.recordBloodPressure(120, 80, false, T0);
        assertEquals("120/80", record.getFactAnswers().get("BP_READING"));
        assertThrows(IllegalArgumentException.class,
            () -> record.recordBloodPressure(120, null, false, T0));
    }
    @Test
    void explicitCorrectionReopensWarningAndResetsPreviousCooldown() {
        var record = new MedicalSafetyRouteStateEntity("user", "R10", T0);
        record.begin("day-1", "P19", T0);
        record.answer("F_FLUID_LEAK", "YES", T0);
        record.update(complete("URGENT", "OB1"), T0, 72);
        assertEquals("COMPLETE", record.getState());
        record.reopenForCorrection(T0.plusSeconds(1));
        assertEquals("QUESTION", record.getState());
        assertNull(record.getCompletedAt());
        assertNull(record.getCooldownUntil());
        record.answer("F_FLUID_LEAK", "NO", T0.plusSeconds(2));
        assertEquals("NO", record.getFactAnswers().get("F_FLUID_LEAK"));
        record.update(complete("NOT_REPORTED", "H1"), T0.plusSeconds(2), 72);
        assertEquals(T0.plusSeconds(2), record.getCompletedAt());
    }

    @Test
    void recordsClinicalPendingConditionAndActualRuleVersionForAudit() {
        var record = new MedicalSafetyRouteStateEntity("person", "R12", T0);
        record.begin("s", "PP5", T0);
        record.answer("F_PAD_SOAK_1H", "YES", T0);
        record.update(new MedicalStep(
            "R12", "POSTPARTUM_BLEEDING", MedicalSafetyCatalog.VERSION,
            MedicalSafetyCatalog.RULE_VERSION, "COMPLETE", "EMERGENCY", "E",
            "CALL_EMERGENCY", null, "CONDITION_UNDEFINED"
        ), T0, 24);
        assertEquals("CONDITION_UNDEFINED", record.getConditionStatus());
        assertEquals(MedicalSafetyCatalog.RULE_VERSION, record.getRuleVersion());
    }

}
