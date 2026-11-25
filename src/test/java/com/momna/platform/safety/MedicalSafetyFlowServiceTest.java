package com.momna.platform.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.platform.safety.MedicalSafetyFlowService.ItemAnswer;
import com.momna.platform.safety.MedicalSafetyFlowService.ScoreContribution;
import com.momna.platform.safety.MedicalSafetyFlowService.Trigger;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The MOMNA-1145 Check-in flow over the real v2.2 catalog and resolver, with in-memory persistence. */
class MedicalSafetyFlowServiceTest {
    private static final MedicalSafetyCatalog CATALOG = new MedicalSafetyCatalog(new ObjectMapper());
    private static final String USER = "person-1";

    private final MovingClock clock = new MovingClock(Instant.parse("2026-11-25T07:00:00Z"));
    private final InMemoryRouteStates store = new InMemoryRouteStates();
    private final MedicalSafetyFlowService flow = new MedicalSafetyFlowService(
        CATALOG, new MedicalSafetyResolver(), store.repository, store.revisions, clock);

    private List<MedicalStep> patch(String session, String period, String item, Integer value) {
        return flow.onCheckinPatch(new Trigger(USER, session, period, List.of(new ItemAnswer(item, value)),
            value == null ? Map.of() : Map.of(item, value), false, List.of()));
    }

    private static MedicalStep route(List<MedicalStep> steps, String routeId) {
        return steps.stream().filter(s -> s.routeId().equals(routeId)).findFirst().orElse(null);
    }

    /** Answers every remaining question of a route with the same value. */
    private List<MedicalStep> answerAll(String session, String routeId, String value) {
        List<MedicalStep> steps = flow.sessionSteps(USER, session);
        for (int guard = 0; guard < 20; guard++) {
            var step = route(steps, routeId);
            if (step == null || !step.question()) return steps;
            steps = "QM".equals(step.screenCode())
                ? flow.answerChecklist(USER, session, routeId, "NO".equals(value) ? "NONE" : "UNSURE", List.of())
                : flow.answer(USER, session, routeId, step.nextFactCode(), value);
        }
        throw new AssertionError("route never completed");
    }

    @Test
    void d12AStateTwoOpensItsRouteWithoutAnySafetyScore() {
        var steps = patch("s1", "PREGNANCY", "P19", 2);
        var r10 = route(steps, "R10");
        assertEquals("QUESTION", r10.state());
        assertEquals("F_FLUID_LEAK", r10.nextFactCode());
        assertEquals("PREGNANCY_PRETERM_LABOR", r10.routeCode());
    }

    @Test
    void d12StatesZeroAndOneNeverOpenARouteDirectly() {
        assertTrue(patch("s1", "PREGNANCY", "P19", 1).isEmpty());
        assertTrue(patch("s1", "PREGNANCY", "P19", 0).isEmpty());
        assertTrue(patch("s1", "CYCLE", "P19", 2).isEmpty(), "an item of another period opens nothing");
    }

    @Test
    void d12Pp2OpensR18AtEmotionallyHardOnlyNeverAtCannotTell() {
        assertTrue(patch("s1", "POSTPARTUM", "PP2", 2).isEmpty());
        var steps = flow.onCheckinPatch(new Trigger(USER, "s1", "POSTPARTUM", List.of(new ItemAnswer("PP2", 2)),
            Map.of("PP2", 2), true, List.of(new ScoreContribution("PP2", BigDecimal.valueOf(14)))));
        assertTrue(steps.isEmpty(), "PP2 at 2 is uncertainty, also on the cumulative path");
        var r18 = route(patch("s1", "POSTPARTUM", "PP2", 1), "R18");
        assertEquals("F_HARM_THOUGHTS", r18.nextFactCode(), "harm is asked first");
    }

    @Test
    void theCumulativeScoreStaysASecondEntryForTheStrongestBoundItem() {
        var steps = flow.onCheckinPatch(new Trigger(USER, "s1", "MENARCHE",
            List.of(new ItemAnswer("M3", 1), new ItemAnswer("M4", 1)), Map.of("M3", 1, "M4", 1), true,
            List.of(new ScoreContribution("M3", BigDecimal.valueOf(2)), new ScoreContribution("M4", BigDecimal.valueOf(7)))));
        assertEquals(List.of("R2"), steps.stream().map(MedicalStep::routeId).toList(),
            "M4 dizziness (7) outweighs M3 headache (2)");
    }

    @Test
    void oneEventMatchingSeveralRoutesOpensEachWithoutAskingAFactTwice() {
        flow.onCheckinPatch(new Trigger(USER, "s1", "POSTPARTUM",
            List.of(new ItemAnswer("PP5", 2), new ItemAnswer("PP14", 2)), Map.of("PP5", 2, "PP14", 2), false, List.of()));
        // R12 asks F_FAINTED first; R16 asks it third. Answering it on R12 answers it for the whole session.
        var steps = flow.answer(USER, "s1", "R12", "F_FAINTED", "NO");
        steps = flow.answer(USER, "s1", "R16", route(steps, "R16").nextFactCode(), "NO");
        steps = flow.answer(USER, "s1", "R16", route(steps, "R16").nextFactCode(), "NO");
        assertNotEquals("F_FAINTED", route(steps, "R16").nextFactCode());
    }

    @Test
    void l13ACompletedRouteWithoutYesPausesTheSameItemForItsWindow() {
        patch("day1", "PREGNANCY", "P19", 2);
        answerAll("day1", "R10", "NO");
        assertEquals("NOT_REPORTED", route(flow.sessionSteps(USER, "day1"), "R10").disposition());

        clock.advance(Duration.ofHours(71));
        assertTrue(patch("day3", "PREGNANCY", "P19", 2).isEmpty(), "R10 pauses 72 h");
        clock.advance(Duration.ofHours(1));
        assertEquals("QUESTION", route(patch("day4", "PREGNANCY", "P19", 2), "R10").state());
    }

    @Test
    void l13ANewItemOrAWorseningBypassesThePause() {
        patch("day1", "PREGNANCY", "P19", 2);
        answerAll("day1", "R10", "NO");
        clock.advance(Duration.ofHours(2));
        assertEquals("QUESTION", route(patch("day1-evening", "PREGNANCY", "P5", 2), "R10").state(),
            "P5 is a new symptom on R10");

        patch("day2", "POSTPARTUM", "PP4", 2);
        answerAll("day2", "R11", "NO");
        clock.advance(Duration.ofHours(1));
        assertTrue(patch("day2-evening", "POSTPARTUM", "PP4", 2).isEmpty(), "the same item stays paused");
        patch("day3", "POSTPARTUM", "PP4", 0);
        clock.advance(Duration.ofHours(1));
        assertEquals("QUESTION", route(patch("day3-evening", "POSTPARTUM", "PP4", 2), "R11").state(),
            "lowered, then raised again: a worsening");
    }

    @Test
    void aSessionNeverRunsARouteTwiceEvenWhenTheAnswerGoesDownAndUp() {
        patch("s1", "PREGNANCY", "P19", 2);
        answerAll("s1", "R10", "NO");
        patch("s1", "PREGNANCY", "P19", 0);
        var again = route(patch("s1", "PREGNANCY", "P19", 2), "R10");
        assertEquals("COMPLETE", again.state());
    }

    @Test
    void loweringTheOpeningAnswerClosesItsQuestionWithoutAYes() {
        assertTrue(route(patch("s1", "PREGNANCY", "P19", 2), "R10").question());
        var closed = route(patch("s1", "PREGNANCY", "P19", 1), "R10");
        assertEquals("COMPLETE", closed.state());
        assertFalse(store.row(USER, "R10").getFactAnswers().containsValue("NO"), "remaining facts are not NO");
    }

    @Test
    void aYesWarningIsShownInLaterSessionsWhileItsPauseLasts() {
        patch("day1", "PREGNANCY", "P19", 2);
        var steps = flow.answer(USER, "day1", "R10", "F_FLUID_LEAK", "YES");
        steps = answerAll("day1", "R10", "NO");
        assertEquals("URGENT", route(steps, "R10").disposition());

        clock.advance(Duration.ofHours(24));
        var later = flow.steps(USER, "day2");
        assertEquals("URGENT", route(later, "R10").disposition(), "a pause never hides a YES");
        assertTrue(flow.sessionSteps(USER, "day2").isEmpty(), "but it is not the later session's own decision");
        assertTrue(patch("day2", "PREGNANCY", "P5", 2).stream().noneMatch(MedicalStep::question),
            "and it is not asked again");
        clock.advance(Duration.ofHours(48));
        assertNull(route(flow.steps(USER, "day4"), "R10"), "after the window the warning has run its course");
    }

    @Test
    void onlyTheCurrentQuestionCanBeAnswered() {
        patch("s1", "PREGNANCY", "P19", 2);
        var wrongFact = assertThrows(MedicalSafetyException.class,
            () -> flow.answer(USER, "s1", "R10", "F_CONTRACTIONS_PRETERM", "NO"));
        assertEquals("SAFETY_FACT_NOT_CURRENT", wrongFact.code());
        var wrongValue = assertThrows(MedicalSafetyException.class,
            () -> flow.answer(USER, "s1", "R10", "F_FLUID_LEAK", "MAYBE"));
        assertEquals("SAFETY_VALUE_INVALID", wrongValue.code());
        var otherSession = assertThrows(MedicalSafetyException.class,
            () -> flow.answer(USER, "s2", "R10", "F_FLUID_LEAK", "NO"));
        assertEquals("SAFETY_ROUTE_NOT_CURRENT", otherSession.code());
        var unknownRoute = assertThrows(MedicalSafetyException.class,
            () -> flow.answer(USER, "s1", "R99", "F_FLUID_LEAK", "NO"));
        assertEquals("SAFETY_ROUTE_NOT_CURRENT", unknownRoute.code());
    }

    @Test
    void aCompletedRouteAcceptsAReplayButNotANewAnswer() {
        patch("s1", "PREGNANCY", "P19", 2);
        flow.answer(USER, "s1", "R10", "F_FLUID_LEAK", "YES");
        answerAll("s1", "R10", "NO");
        flow.answer(USER, "s1", "R10", "F_FLUID_LEAK", "YES");
        var changed = assertThrows(MedicalSafetyException.class,
            () -> flow.answer(USER, "s1", "R10", "F_FLUID_LEAK", "NO"));
        assertEquals("SAFETY_ROUTE_COMPLETED", changed.code());
    }

    @Test
    void aCorrectionIsAuditedAndReDecidesTheRoute() {
        patch("s1", "PREGNANCY", "P19", 2);
        flow.answer(USER, "s1", "R10", "F_FLUID_LEAK", "YES");
        assertEquals("URGENT", route(answerAll("s1", "R10", "NO"), "R10").disposition());
        var reopened = route(flow.revise(USER, "s1", "R10", "F_FLUID_LEAK", "NO"), "R10");
        assertTrue(reopened.question(), "the questions an urgent YES had made unnecessary are asked now");
        assertEquals("NOT_REPORTED", route(answerAll("s1", "R10", "NO"), "R10").disposition());
        assertEquals(1, store.revisionRows.size());
        var notAnswered = assertThrows(MedicalSafetyException.class,
            () -> flow.revise(USER, "s1", "R10", "F_CHEST_PAIN", "NO"));
        assertEquals("SAFETY_FACT_NOT_ANSWERED", notAnswered.code());
    }

    @Test
    void qmIsOneAnswerForAllShownSystemicFacts() {
        patch("s1", "POSTPARTUM", "PP14", 2);
        List<MedicalStep> steps = flow.sessionSteps(USER, "s1");
        while (!"QM".equals(route(steps, "R16").screenCode())) {
            steps = flow.answer(USER, "s1", "R16", route(steps, "R16").nextFactCode(), "NO");
        }
        var shown = route(steps, "R16").questionFactCodes();
        assertEquals(5, shown.size());
        assertThrows(MedicalSafetyException.class,
            () -> flow.answerChecklist(USER, "s1", "R16", "SELECTED", List.of()));
        assertThrows(MedicalSafetyException.class,
            () -> flow.answerChecklist(USER, "s1", "R16", "NONE", List.of("F_FEVER_38")));
        assertThrows(MedicalSafetyException.class,
            () -> flow.answerChecklist(USER, "s1", "R16", "SELECTED", List.of("F_CHEST_PAIN")));
        var done = route(flow.answerChecklist(USER, "s1", "R16", "SELECTED", List.of("F_FEVER_38")), "R16");
        assertEquals("URGENT", done.disposition());
        var answers = store.row(USER, "R16").getFactAnswers();
        assertEquals("YES", answers.get("F_FEVER_38"));
        shown.stream().filter(c -> !c.equals("F_FEVER_38")).forEach(c -> assertEquals("NO", answers.get(c), c));
    }

    @Test
    void bp1IsAskedOnlyAfterAMeasuredPressureAndClassifiesBothBands() {
        patch("s1", "POSTPARTUM", "PP19", 2);
        List<MedicalStep> steps = flow.sessionSteps(USER, "s1");
        assertThrows(MedicalSafetyException.class, () -> flow.recordBloodPressure(USER, "s1", "R17", 150, 95, false));
        while (!"F_BP_MEASURED".equals(route(steps, "R17").nextFactCode())) {
            steps = flow.answer(USER, "s1", "R17", route(steps, "R17").nextFactCode(), "NO");
        }
        steps = flow.answer(USER, "s1", "R17", "F_BP_MEASURED", "YES");
        assertEquals("BP1", route(steps, "R17").screenCode());
        var invalid = assertThrows(MedicalSafetyException.class,
            () -> flow.recordBloodPressure(USER, "s1", "R17", 80, 120, false));
        assertEquals("SAFETY_VALUE_INVALID", invalid.code());
        assertEquals("URGENT", route(flow.recordBloodPressure(USER, "s1", "R17", 145, 85, false), "R17").disposition());
    }

    @Test
    void skipClosesTheRemainingQuestionsAsAbsentNeverAsNo() {
        patch("s1", "PREGNANCY", "P19", 2);
        var skipped = route(flow.skip(USER, "s1", "R10"), "R10");
        assertEquals("COMPLETE", skipped.state());
        assertFalse(store.row(USER, "R10").getFactAnswers().containsValue("NO"));
        assertTrue(store.row(USER, "R10").getFactAnswers().values().stream().allMatch("ABSENT"::equals));
    }

    @Test
    void anUnsureUrgentSignEndsUnknownNotSafe() {
        patch("s1", "PREGNANCY", "P19", 2);
        flow.answer(USER, "s1", "R10", "F_FLUID_LEAK", "UNSURE");
        assertEquals("UNKNOWN", route(answerAll("s1", "R10", "NO"), "R10").disposition());
    }

    static final class MovingClock extends Clock {
        private Instant now;

        MovingClock(Instant start) { this.now = start; }

        void advance(Duration by) { now = now.plus(by); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
