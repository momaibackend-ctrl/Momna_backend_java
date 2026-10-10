package com.momna.modules.checkin.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import com.momna.modules.checkin.domain.*;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckinLifecycleTransitionTest {
    private static final Instant START = Instant.parse("2026-10-10T08:00:00Z");

    private CheckinSessionEntity session() {
        return new CheckinSessionEntity(
            "session-1", "user-1", CheckinPhase.MORNING,
            LocalDate.of(2026, 10, 10), "CYCLE", null,
            "Europe/Madrid", 1, "rules-1", List.of("mood", "sleep"),
            START.minusSeconds(60), START.plusSeconds(3600), START
        );
    }

    @Test void initiallyDraft() {
        var s = session();
        assertEquals(CheckinSessionStatus.DRAFT, s.getStatus());
        assertEquals(BigDecimal.ZERO, s.getCompletionRatio());
        assertNull(s.getFinalizedAt());
        assertNull(s.getConsumedAt());
    }

    @Test void draftToPartialAndBackToDraftOnAnswerRemoval() {
        var s = session();
        s.updateCompletion(1, START.plusSeconds(30));
        assertEquals(CheckinSessionStatus.PARTIAL, s.getStatus());
        assertEquals(0, new BigDecimal("0.5").compareTo(s.getCompletionRatio()));
        s.updateCompletion(0, START.plusSeconds(45));
        assertEquals(CheckinSessionStatus.DRAFT, s.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(s.getCompletionRatio()));
    }

    @Test void fullCompletionRemainsPartialUntilExplicitSubmission() {
        var s = session();
        s.updateCompletion(2, START.plusSeconds(30));
        assertEquals(CheckinSessionStatus.PARTIAL, s.getStatus());
        assertEquals(0, BigDecimal.ONE.compareTo(s.getCompletionRatio()));
        s.finalizeByUser(START.plusSeconds(50));
        assertEquals(CheckinSessionStatus.SUBMITTED, s.getStatus());
        assertEquals(CheckinFinalizationReason.USER_SUBMIT, s.getFinalizationReason());
    }

    @Test void serverWindowClosureIsNotUserSubmission() {
        var s = session();
        s.autoFinalize(START.plusSeconds(3600));
        assertEquals(CheckinSessionStatus.AUTO_FINALIZED, s.getStatus());
        assertEquals(CheckinFinalizationReason.WINDOW_CLOSED, s.getFinalizationReason());
        assertEquals(START.plusSeconds(3600), s.getFinalizedAt());
    }

    @Test void consumingDoesNotChangeFinalizationStatus() {
        var s = session();
        s.finalizeByUser(START.plusSeconds(15));
        s.markConsumed("day-a", START.plusSeconds(20));
        assertEquals(CheckinSessionStatus.SUBMITTED, s.getStatus());
        assertEquals("day-a", s.getConsumedByRef());
    }

    @Test void consumingSameMyDayTwiceIsIdempotent() {
        var s = session();
        s.markConsumed("day-a", START.plusSeconds(20));
        s.markConsumed("day-a", START.plusSeconds(21));
        assertEquals("day-a", s.getConsumedByRef());
    }

    @Test void consumingDifferentMyDayIsRejected() {
        var s = session();
        s.markConsumed("day-a", START.plusSeconds(20));
        assertThrows(IllegalStateException.class,
            () -> s.markConsumed("day-b", START.plusSeconds(21)));
    }
}
