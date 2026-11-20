package com.momna.modules.checkin.myday;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.momna.modules.checkin.domain.CheckinPhase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CheckinMyDaySourceDateTest {
    @Test
    void usesPreviousEveningAndSameMorning() {
        var target = LocalDate.of(2026, 10, 13);
        assertEquals(target, CheckinMyDayOrchestrator.sourceDateFor(CheckinPhase.MORNING, target));
        assertEquals(LocalDate.of(2026, 10, 12), CheckinMyDayOrchestrator.sourceDateFor(CheckinPhase.EVENING, target));
    }

    @Test
    void survivesYearBoundaryAndLeapDay() {
        assertEquals(LocalDate.of(2025, 12, 31),
            CheckinMyDayOrchestrator.sourceDateFor(CheckinPhase.EVENING, LocalDate.of(2026, 1, 1)));
        assertEquals(LocalDate.of(2024, 2, 29),
            CheckinMyDayOrchestrator.sourceDateFor(CheckinPhase.EVENING, LocalDate.of(2024, 3, 1)));
    }
}
