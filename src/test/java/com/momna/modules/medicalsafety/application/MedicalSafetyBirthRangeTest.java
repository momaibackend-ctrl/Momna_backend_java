package com.momna.modules.medicalsafety.application;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** WB1 (day 28 to 42 after birth) estimates the birth from the middle of the month on the design's wheel (V41). */
class MedicalSafetyBirthRangeTest {
    @Test
    void eachMonthHasItsMiddleAndTheOthersNone() {
        assertEquals(14, MedicalSafetyApplicationService.rangeMiddleDays("less_than_month"));
        for (int month = 1; month <= 11; month++) {
            assertEquals(month * 30 + 15, MedicalSafetyApplicationService.rangeMiddleDays("month_" + month));
        }
        assertNull(MedicalSafetyApplicationService.rangeMiddleDays("month_12"));
        assertNull(MedicalSafetyApplicationService.rangeMiddleDays("m1_3"), "the V39 ranges are gone");
        assertNull(MedicalSafetyApplicationService.rangeMiddleDays("more_than_year"));
    }
}
