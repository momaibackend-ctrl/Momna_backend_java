package com.momna.modules.checkin.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import com.momna.modules.checkin.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CheckinLifecycleTransitionTest {
  private CheckinSessionEntity session() {
    var now = Instant.parse("2026-10-10T08:00:00Z");
    return new CheckinSessionEntity("session-1", "user-1", CheckinPhase.MORNING, LocalDate.of(2026,10,10), "CYCLE", null, "Europe/Madrid", 1, "rules-1", List.of("mood", "sleep"), now.minusSeconds(60), now.plusSeconds(3600), now);
  }
  @Test void draftPartialDraft() {
    var s = session();
    assertEquals(CheckinSessionStatus.DRAFT, s.getStatus());
    s.updateCompletion(1, Instant.now());
    assertEquals(CheckinSessionStatus.PARTIAL, s.getStatus());
    s.updateCompletion(0, Instant.now());
    assertEquals(CheckinSessionStatus.DRAFT, s.getStatus());
  }
  @Test void distinctManualAndAutomaticFinalization() {
    var a = session();
    a.finalizeByUser(Instant.now());
    assertEquals(CheckinSessionStatus.SUBMITTED, a.getStatus());
    assertEquals(CheckinFinalizationReason.USER_SUBMIT, a.getFinalizationReason());
    var b = session();
    b.autoFinalize(Instant.now());
    assertEquals(CheckinSessionStatus.AUTO_FINALIZED, b.getStatus());
    assertEquals(CheckinFinalizationReason.WINDOW_CLOSED, b.getFinalizationReason());
  }
  @Test void consumptionIsIdempotentOnlyForSameMyDay() {
    var s = session();
    s.markConsumed("day-1", Instant.now());
    s.markConsumed("day-1", Instant.now());
    assertEquals("day-1", s.getConsumedByRef());
    assertThrows(IllegalStateException.class, () -> s.markConsumed("day-2", Instant.now()));
  }
}
