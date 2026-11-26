package com.momna.modules.medicalsafety.application;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** WB1 (day 28 to 42 after birth) estimates the birth from the middle of a time-since-birth range (V39). */
class MedicalSafetyBirthRangeTest {
    @Test
    void eachRangeHasItsMiddleAndTheOthersNone() {
        assertEquals(14, MedicalSafetyApplicationService.rangeMiddleDays("lt_1m"));
        assertEquals(60, MedicalSafetyApplicationService.rangeMiddleDays("m1_3"));
        assertEquals(150, MedicalSafetyApplicationService.rangeMiddleDays("m4_6"));
        assertEquals(240, MedicalSafetyApplicationService.rangeMiddleDays("m7_9"));
        assertEquals(330, MedicalSafetyApplicationService.rangeMiddleDays("m10_12"));
        assertNull(MedicalSafetyApplicationService.rangeMiddleDays("more_than_year"));
        assertNull(MedicalSafetyApplicationService.rangeMiddleDays("prefer_not_to_say"));
        assertNull(MedicalSafetyApplicationService.rangeMiddleDays("month_3"), "the old monthly codes are gone");
    }
}
