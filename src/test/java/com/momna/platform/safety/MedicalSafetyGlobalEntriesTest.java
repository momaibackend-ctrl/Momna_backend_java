package com.momna.platform.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.platform.safety.MedicalSafetyFlowService.ItemAnswer;
import com.momna.platform.safety.MedicalSafetyFlowService.Trigger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The global v2.2 entries (MOMNA-1145 §8, §9.4, §12): ER0, WB1 and calendar signals. */
class MedicalSafetyGlobalEntriesTest {
    private static final MedicalSafetyCatalog CATALOG = new MedicalSafetyCatalog(new ObjectMapper());
    private static final MedicalSafetyEr0Catalog ER0 = new MedicalSafetyEr0Catalog(new ObjectMapper());
    private static final String USER = "person-1";

    private final MedicalSafetyFlowServiceTest.MovingClock clock =
        new MedicalSafetyFlowServiceTest.MovingClock(Instant.parse("2026-11-25T07:00:00Z"));
    private final InMemoryRouteStates store = new InMemoryRouteStates();
    private final MedicalSafetyFlowService flow = new MedicalSafetyFlowService(
        CATALOG, new MedicalSafetyResolver(), store.repository, store.revisions, store.wellbeing, clock);

    private MedicalStep tap(String period, String code) {
        var option = ER0.option(period, code);
        return flow.emergencyEntry(USER, period, option.routeId(), option.code()).step();
    }

    @Test
    void er0ShowsTheCommonSignsAndUpToThreeOfThePeriod() {
        assertEquals(10, ER0.screen("CYCLE").options().size());
        assertEquals(13, ER0.screen("PREGNANCY").options().size());
        assertEquals(13, ER0.screen("POSTPARTUM").options().size());
        var harm = ER0.screen("POSTPARTUM").options().stream().filter(o -> o.code().equals("F_HARM_THOUGHTS"))
            .findFirst().orElseThrow();
        assertEquals("Мысли причинить вред себе или ребёнку", harm.label().ru());
        assertEquals("Что происходит прямо сейчас?", ER0.screen("CYCLE").title().ru());
        var notShown = assertThrows(MedicalSafetyException.class, () -> ER0.option("CYCLE", "F_FLUID_LEAK"));
        assertEquals("SAFETY_SIGNAL_NOT_APPLICABLE", notShown.code());
    }

    @Test
    void anEmergencySignIsOneTapToTheEmergencyScreen() {
        var step = tap("CYCLE", "F_CHEST_PAIN");
        assertEquals("R26", step.routeId());
        assertEquals("GLOBAL_EMERGENCY_QUICK_CHECK", step.routeCode());
        assertEquals("EMERGENCY", step.disposition());
        assertEquals("E", step.screenCode());
        assertEquals("CALL_EMERGENCY", step.actionCode());
        assertEquals("CRITICAL", step.severity());
        var record = store.row(USER, "R26");
        assertEquals(Map.of("F_CHEST_PAIN", "YES"), record.getFactAnswers(), "the other signs stay unanswered");
        assertNull(record.getCooldownUntil(), "ER0 is never paused");
    }

    @Test
    void thePeriodDecidesTheScreenOfAPregnancySign() {
        var step = tap("PREGNANCY", "F_FLUID_LEAK");
        assertEquals("URGENT", step.disposition());
        assertEquals("OB1", step.screenCode());
        assertEquals("OB1", flow.globalOutcomes(USER, Instant.EPOCH, clock.instant().plusSeconds(1))
            .getFirst().screenCode(), "re-reading the outcome keeps the period it was decided in");
    }

    @Test
    void tapsOfOneEpisodeAddUpAndALaterEpisodeStartsAfresh() {
        tap("CYCLE", "F_HEART_RACING");
        clock.advance(Duration.ofMinutes(5));
        var second = tap("CYCLE", "F_FAINTED");
        assertEquals("EMERGENCY", second.disposition());
        assertEquals(Map.of("F_HEART_RACING", "YES", "F_FAINTED", "YES"), store.row(USER, "R26").getFactAnswers());
        clock.advance(Duration.ofHours(25));
        tap("CYCLE", "F_HEART_RACING");
        assertEquals(Map.of("F_HEART_RACING", "YES"), store.row(USER, "R26").getFactAnswers());
    }

    @Test
    void noneOfTheseRecordsNoForTheShownSignsAndAlarmsNothing() {
        var shown = ER0.screen("CYCLE").options().stream().map(MedicalSafetyEr0Catalog.Option::code)
            .filter(c -> c.startsWith("F_")).toList();
        var step = flow.emergencyNone(USER, "CYCLE", shown).step();
        assertEquals("NOT_REPORTED", step.disposition());
        assertEquals("NONE", step.severity());
        assertTrue(store.row(USER, "R26").getFactAnswers().values().stream().allMatch("NO"::equals));
    }

    @Test
    void facialSwellingAsksItsTwoQuestionsAndAirwaySwellingIsAnAllergicEmergency() {
        var first = tap("CYCLE", "ALLERGY_SWELLING");
        assertEquals("R25", first.routeId());
        assertEquals("F_AIRWAY_SWELLING", first.nextFactCode());
        var done = flow.answerRoute(USER, "R25", "F_AIRWAY_SWELLING", "YES").step();
        assertEquals("EMERGENCY", done.disposition());
        assertEquals("AG1", done.screenCode());
        var wrong = assertThrows(MedicalSafetyException.class,
            () -> flow.answerRoute(USER, "R25", "F_AIRWAY_SWELLING", "NO"));
        assertEquals("SAFETY_ROUTE_COMPLETED", wrong.code());
    }

    @Test
    void postpartumPreeclampsiaFromEr0AsksItsQuestionsAndTheBloodPressure() {
        var step = tap("POSTPARTUM", "POSTPARTUM_PREECLAMPSIA");
        assertEquals("R17", step.routeId());
        for (int guard = 0; guard < 10 && !"F_BP_MEASURED".equals(step.nextFactCode()); guard++) {
            step = flow.answerRoute(USER, "R17", step.nextFactCode(), "NO").step();
        }
        step = flow.answerRoute(USER, "R17", "F_BP_MEASURED", "YES").step();
        assertEquals("BP_READING", step.nextFactCode());
        assertEquals("EMERGENCY", flow.routeBloodPressure(USER, "R17", 165, 100, false).step().disposition());
    }

    @Test
    void aGlobalEntryJoinsAnOpenCheckinQuestionOfItsRoute() {
        flow.onCheckinPatch(new Trigger(USER, "s1", "POSTPARTUM", List.of(new ItemAnswer("PP19", 2)),
            Map.of("PP19", 2), false, List.of()));
        var joined = flow.emergencyEntry(USER, "POSTPARTUM", "R17", "POSTPARTUM_PREECLAMPSIA");
        assertEquals("s1", joined.sessionId(), "the session keeps its question");
        assertTrue(joined.step().question());
        var answered = flow.answerRoute(USER, "R17", joined.step().nextFactCode(), "NO");
        assertEquals("s1", answered.sessionId());
        assertEquals(1, flow.sessionSteps(USER, "s1").size());
    }

    @Test
    void calendarSignalsDecideWithoutDailyQuestions() {
        var postcoital = flow.calendarSignal(USER, "CYCLE", "POSTCOITAL_BLEED").step();
        assertEquals("R4", postcoital.routeId());
        assertEquals("PROMPT", postcoital.disposition());
        assertEquals("MEDIUM", postcoital.severity());
        assertFalse(store.row(USER, "R4").getFactAnswers().containsValue("NO"), "history is never NO");

        var menopause = flow.calendarSignal(USER, "MENOPAUSE", "BLEEDING_RECORDED").step();
        assertEquals("MB1", menopause.screenCode());
        var pregnancy = flow.calendarSignal(USER, "PREGNANCY", "PREGNANCY_VAGINAL_BLEED").step();
        assertEquals("R9", pregnancy.routeId());
        assertEquals("URGENT", pregnancy.disposition());

        var wrong = assertThrows(MedicalSafetyException.class,
            () -> flow.calendarSignal(USER, "MENOPAUSE", "POSTCOITAL_BLEED"));
        assertEquals("SAFETY_SIGNAL_NOT_APPLICABLE", wrong.code());
    }

    @Test
    void wb1IsShownOnceInItsWindowAndHardLatelyOpensR18HarmFirst() {
        assertFalse(flow.wellbeingStatus(USER, false).show(), "outside its window");
        assertTrue(flow.wellbeingStatus(USER, true).show());

        var hard = flow.answerWellbeing(USER, "POSTPARTUM", "HARD_LATELY").step();
        assertEquals("R18", hard.routeId());
        assertEquals("F_HARM_THOUGHTS", hard.nextFactCode());
        assertEquals(hard, flow.answerWellbeing(USER, "POSTPARTUM", "HARD_LATELY").step(), "a replay continues");
        assertFalse(flow.wellbeingStatus(USER, true).show(), "never again after an answer");
        var other = assertThrows(MedicalSafetyException.class,
            () -> flow.answerWellbeing(USER, "POSTPARTUM", "MOSTLY_GOOD"));
        assertEquals("SAFETY_ROUTE_COMPLETED", other.code());

        var mood = flow.answerRoute(USER, "R18", "F_HARM_THOUGHTS", "NO").step();
        mood = flow.answerRoute(USER, "R18", mood.nextFactCode(), "YES").step();
        assertEquals("PROMPT", mood.disposition());
        assertEquals("PM1", mood.screenCode());
    }

    @Test
    void wb1MostlyGoodClosesTheCardAndIsNotShownWhileR18RanRecently() {
        assertNull(flow.answerWellbeing("other", "POSTPARTUM", "MOSTLY_GOOD").step());
        assertFalse(flow.wellbeingStatus("other", true).show());

        flow.onCheckinPatch(new Trigger(USER, "s1", "POSTPARTUM", List.of(new ItemAnswer("PP18", 2)),
            Map.of("PP18", 2), false, List.of()));
        assertFalse(flow.wellbeingStatus(USER, true).show(), "R18 is running");
        var wrongPeriod = assertThrows(MedicalSafetyException.class,
            () -> flow.answerWellbeing(USER, "CYCLE", "HARD_LATELY"));
        assertEquals("SAFETY_SIGNAL_NOT_APPLICABLE", wrongPeriod.code());
    }

    @Test
    void myDayReadsOnlyCompletedGlobalOutcomesOfItsWindow() {
        tap("CYCLE", "F_CHEST_PAIN");
        tap("CYCLE", "ALLERGY_SWELLING");
        var now = clock.instant();
        var outcomes = flow.globalOutcomes(USER, now.minusSeconds(60), now.plusSeconds(60));
        assertEquals(List.of("R26"), outcomes.stream().map(MedicalStep::routeId).toList(), "R25 is still open");
        assertTrue(flow.globalOutcomes(USER, now.plusSeconds(60), now.plusSeconds(120)).isEmpty());
    }
}
