package com.momna.modules.myday;

import static org.junit.jupiter.api.Assertions.*;

import com.momna.modules.myday.application.MyDayPlanRules;
import com.momna.modules.myday.infrastructure.MyDayCardEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

/** Product rules of 23.11.2026 (MOMNA-989 sections 2-4) as invariants. */
class MyDayPlanRulesPropertyTest {
    private static final List<String> CARDS = List.of("am-1", "am-2", "am-3", "pm-1", "pm-2", "pm-3",
        "optional-1", "optional-2", "optional-3");
    private static final List<String> STATES = List.of("PENDING", "COMPLETED", "SKIPPED");

    @Provide
    Arbitrary<BigDecimal> share() {
        return Arbitraries.oneOf(Arbitraries.just(BigDecimal.ZERO),
            Arbitraries.integers().between(0, 1_000_000).map(i -> BigDecimal.valueOf(i, 6)));
    }

    /** Personal exactly when some Check-in contributes; standard only when none does. */
    @Property(tries = 500, seed = "20261123")
    void aPlanIsPersonalExactlyWhenACheckinContributes(@ForAll @Size(max = 2) List<@From("share") BigDecimal> shares) {
        var expected = shares.stream().anyMatch(s -> s.signum() > 0) ? "PERSONAL" : "STANDARD";
        assertEquals(expected, MyDayPlanRules.kind(shares));
    }

    /**
     * The second button of screen A always names the plan it produces: before any plan, a plan made
     * without the morning Check-in has only Evening(D-1) as a possible source.
     */
    @Property(tries = 500, seed = "20261124")
    void theSecondChoiceAlwaysMatchesThePlanItProduces(@ForAll("share") BigDecimal eveningShare) {
        var secondary = MyDayPlanRules.secondaryChoice(MyDayPlanRules.contributes(eveningShare));
        var kind = MyDayPlanRules.kind(List.of(eveningShare));
        assertEquals("FROM_EVENING_CHECKIN".equals(secondary), "PERSONAL".equals(kind));
        assertEquals("GENERAL".equals(secondary), "STANDARD".equals(kind));
    }

    @Test
    void allFourCombinationsOfTheProductTable() {
        var none = BigDecimal.ZERO;
        var some = new BigDecimal("0.300000");
        assertEquals("PERSONAL", MyDayPlanRules.kind(List.of(new BigDecimal("0.700000"), some)));
        assertEquals("PERSONAL", MyDayPlanRules.kind(List.of(some)));
        assertEquals("PERSONAL", MyDayPlanRules.kind(List.of(new BigDecimal("1.000000"))));
        assertEquals("STANDARD", MyDayPlanRules.kind(List.of()));
        assertEquals("STANDARD", MyDayPlanRules.kind(List.of(none)), "a final Check-in without answers does not count");
    }

    record Op(int card, int state) {}

    @Provide
    Arbitrary<List<Op>> ops() {
        return Combinators.combine(Arbitraries.integers().between(0, 8), Arbitraries.integers().between(0, 2))
            .as(Op::new).list().ofMaxSize(80);
    }

    /**
     * Any sequence of marks: only real changes apply, and the day completes at most once, exactly at the
     * first moment every AM and PM card is COMPLETED, even when marks are taken back and redone.
     */
    @Property(tries = 400, seed = "20261125")
    void progressTogglesBothWaysAndTheDayCompletesAtMostOnce(@ForAll("ops") List<Op> ops) {
        var status = new LinkedHashMap<String, String>();
        var slot = new LinkedHashMap<String, String>();
        for (var card : CARDS) {
            status.put(card, "PENDING");
            slot.put(card, card.substring(0, card.indexOf('-')).toUpperCase());
        }
        int published = 0;
        boolean everComplete = false;
        for (var op : ops) {
            var card = CARDS.get(op.card());
            var to = STATES.get(op.state());
            boolean allowed = MyDayPlanRules.allowedTransition(status.get(card), to);
            assertEquals(!status.get(card).equals(to), allowed, "every real change is allowed, a repeat is not");
            if (!allowed) continue;
            status.put(card, to);
            boolean complete = MyDayPlanRules.dayComplete(status, slot);
            assertEquals(complete, amPmAllCompleted(status), "optional cards never count");
            if (complete && published == 0) published++;
            everComplete |= complete;
        }
        assertTrue(published <= 1);
        assertEquals(everComplete ? 1 : 0, published);
    }

    @Property(tries = 200, seed = "20261126")
    void aPendingCardHasNoChangeTimeAfterAnyMark(@ForAll @IntRange(min = 0, max = 2) int first,
                                                 @ForAll @IntRange(min = 0, max = 2) int second) {
        var card = new MyDayCardEntity("day", "am-1", "AM", 1, "t", "m", "w", "y", "h", "i", "s");
        var at = Instant.parse("2026-11-23T08:00:00Z");
        for (var state : List.of(STATES.get(first), STATES.get(second))) {
            card.changeStatus(state, at);
            assertEquals("PENDING".equals(state), card.getStatusChangedAt() == null);
        }
    }

    private static boolean amPmAllCompleted(Map<String, String> status) {
        return status.entrySet().stream().filter(e -> !e.getKey().startsWith("optional"))
            .allMatch(e -> "COMPLETED".equals(e.getValue()));
    }
}
