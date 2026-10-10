package com.momna.modules.checkin.infrastructure;

import com.momna.modules.checkin.domain.*;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheckinStatePropertyTest {
    private static final Instant AT = Instant.parse("2026-10-10T08:00:00Z");
    private CheckinSessionEntity fresh(int size) {
        var items = java.util.stream.IntStream.range(0,size).mapToObj(i -> "item"+i).toList();
        return new CheckinSessionEntity("session", "user", CheckinPhase.MORNING,
            LocalDate.of(2026,10,10), "CYCLE", null, "Europe/Madrid",
            1, "rules-1", items, AT.minusSeconds(10), AT.plusSeconds(3600), AT);
    }
    @Property(tries=250, seed="20261010")
    void answerCountsDriveDraftAndPartialWithoutFinalizing(
        @ForAll @IntRange(min=1,max=30) int total,
        @ForAll @IntRange(min=0,max=30) int answered
    ) {
        var s = fresh(total);
        int count = Math.min(answered,total);
        s.updateCompletion(count, AT.plusSeconds(1));
        assertEquals(count==0 ? CheckinSessionStatus.DRAFT : CheckinSessionStatus.PARTIAL,s.getStatus());
        assertEquals(0, BigDecimal.valueOf((double)count/total).compareTo(s.getCompletionRatio()));
        assertNull(s.getFinalizedAt());
    }
    @Property(tries=250, seed="20261011")
    void removingAllAnswersReturnsToDraft(
        @ForAll @IntRange(min=1,max=30) int count
    ) {
        var s=fresh(count);
        s.updateCompletion(count,AT.plusSeconds(1));
        s.updateCompletion(0,AT.plusSeconds(2));
        assertEquals(CheckinSessionStatus.DRAFT,s.getStatus());
        assertEquals(0,s.getCompletionRatio().signum());
    }
    @Property(tries=250, seed="20261012")
    void finalizationReasonsNeverCollapse(
        @ForAll boolean automatic,
        @ForAll @IntRange(min=1,max=5000) int elapsed
    ) {
        var s=fresh(2);
        var when=AT.plusSeconds(elapsed);
        if (automatic) {
            s.autoFinalize(when);
            assertEquals(CheckinSessionStatus.AUTO_FINALIZED,s.getStatus());
            assertEquals(CheckinFinalizationReason.WINDOW_CLOSED,s.getFinalizationReason());
        } else {
            s.finalizeByUser(when);
            assertEquals(CheckinSessionStatus.SUBMITTED,s.getStatus());
            assertEquals(CheckinFinalizationReason.USER_SUBMIT,s.getFinalizationReason());
        }
        assertEquals(when,s.getFinalizedAt());
    }
    @Property(tries=250, seed="20261013")
    void consumptionIsIdempotentForSameMyDayAndRejectsOther(
        @ForAll @IntRange(min=1,max=100000) int myDay
    ) {
        var s=fresh(2);
        String id="day-"+myDay;
        s.finalizeByUser(AT.plusSeconds(1));
        s.markConsumed(id,AT.plusSeconds(2));
        s.markConsumed(id,AT.plusSeconds(3));
        assertEquals(id,s.getConsumedByRef());
        assertEquals(CheckinSessionStatus.SUBMITTED,s.getStatus());
        assertThrows(IllegalStateException.class,
            () -> s.markConsumed(id+"-other",AT.plusSeconds(4)));
    }
    @Test void initialSessionIsDraft() {
        var s=fresh(2);
        assertEquals(CheckinSessionStatus.DRAFT,s.getStatus());
        assertNull(s.getFinalizedAt());
    }
}
