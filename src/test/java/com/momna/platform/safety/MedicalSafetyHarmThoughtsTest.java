package com.momna.platform.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.platform.safety.MedicalSafetyFlowService.ItemAnswer;
import com.momna.platform.safety.MedicalSafetyFlowService.Trigger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Catalog v2.3 (Product decision 28.11.2026, MOMNA-1146): «Not now» and «Not sure» on the R18 harm question.
 * «Not now» is «not answered» (H1, a 3-day pause, nothing for My Day); «Not sure» continues and, with nothing
 * higher, ends on the PM1 variant with the urgent-help link and a 7-day pause; «nothing found» keeps 14 days;
 * a YES is PM2 as before.
 */
class MedicalSafetyHarmThoughtsTest {
    private static final MedicalSafetyCatalog CATALOG = new MedicalSafetyCatalog(new ObjectMapper());
    private static final String USER = "person-1";

    private final MedicalSafetyFlowServiceTest.MovingClock clock =
        new MedicalSafetyFlowServiceTest.MovingClock(Instant.parse("2026-11-28T07:00:00Z"));
    private final InMemoryRouteStates store = new InMemoryRouteStates();
    private final MedicalSafetyFlowService flow = new MedicalSafetyFlowService(
        CATALOG, new MedicalSafetyResolver(), store.repository, store.revisions, store.wellbeing, clock);

    private MedicalStep hardLately() {
        var step = flow.answerWellbeing(USER, "POSTPARTUM", "HARD_LATELY").step();
        assertEquals("F_HARM_THOUGHTS", step.nextFactCode());
        return step;
    }

    private Instant pausedUntil() {
        return store.row(USER, "R18").getCooldownUntil();
    }

    @Test
    void notNowOnTheHarmQuestionIsNotAnsweredWithH1AndAThreeDayPause() {
        hardLately();
        var start = clock.instant();
        var step = flow.skipRoute(USER, "R18").step();
        assertEquals("COMPLETE", step.state());
        assertEquals("NOT_ANSWERED", step.disposition());
        assertEquals("H1", step.screenCode());
        assertEquals("OPEN_ER0", step.actionCode());
        assertEquals("NONE", step.severity(), "My Day gets nothing");
        assertEquals("ABSENT", store.row(USER, "R18").getFactAnswers().get("F_HARM_THOUGHTS"));
        assertFalse(store.row(USER, "R18").getFactAnswers().containsValue("NO"), "a skip is never NO");
        assertEquals(start.plus(Duration.ofHours(72)), pausedUntil());
        assertEquals(step, flow.skipRoute(USER, "R18").step(), "a replay returns the outcome");
    }

    @Test
    void anotherUsersOrAnUnknownRouteIsNotFoundAndTheOwnersRouteIsUntouched() {
        hardLately();
        for (var call : List.<Runnable>of(
            () -> flow.skipRoute("intruder", "R18"),
            () -> flow.answerRoute("intruder", "R18", "F_HARM_THOUGHTS", "NO"),
            () -> flow.skipRoute(USER, "R99"),
            () -> flow.skipRoute(USER, "R25"))) {
            var failure = assertThrows(MedicalSafetyException.class, call::run);
            assertEquals("SAFETY_ROUTE_NOT_FOUND", failure.code());
        }
        var owner = store.row(USER, "R18");
        assertTrue(owner.question(), "the owner's R18 is still open");
        assertTrue(owner.getFactAnswers().isEmpty());
        assertNull(owner.getCooldownUntil());
    }

    @Test
    void notSureOnTheHarmQuestionContinuesAndEndsOnThePm1VariantWithASevenDayPause() {
        hardLately();
        var start = clock.instant();
        var next = flow.answerRoute(USER, "R18", "F_HARM_THOUGHTS", "UNSURE").step();
        assertTrue(next.question(), "UNSURE does not stop the questions (L11)");
        assertEquals("F_LOW_MOOD_2W", next.nextFactCode());
        while (next.question()) next = flow.answerRoute(USER, "R18", next.nextFactCode(), "NO").step();
        assertEquals("PROMPT", next.disposition());
        assertEquals("PM1-H", next.screenCode());
        assertEquals("MEDIUM", next.severity(), "My Day gets the support signal and the PM1 doctor line");
        assertEquals(start.plus(Duration.ofHours(168)), pausedUntil());
    }

    @Test
    void notSureWithLowMoodStillShowsTheUrgentHelpLink() {
        hardLately();
        flow.answerRoute(USER, "R18", "F_HARM_THOUGHTS", "UNSURE");
        var outcome = flow.answerRoute(USER, "R18", "F_LOW_MOOD_2W", "YES").step();
        assertEquals("PROMPT", outcome.disposition());
        assertEquals("PM1-H", outcome.screenCode());
    }

    @Test
    void nothingFoundKeepsFourteenDaysAndAYesIsStillPm2() {
        hardLately();
        var start = clock.instant();
        var step = flow.answerRoute(USER, "R18", "F_HARM_THOUGHTS", "NO").step();
        while (step.question()) step = flow.answerRoute(USER, "R18", step.nextFactCode(), "NO").step();
        assertEquals("NOT_REPORTED", step.disposition());
        assertEquals("H1", step.screenCode());
        assertEquals(start.plus(Duration.ofHours(CATALOG.route("R18").cooldownHours())), pausedUntil());
        assertEquals(336, CATALOG.route("R18").cooldownHours());

        var other = "person-2";
        flow.answerWellbeing(other, "POSTPARTUM", "HARD_LATELY");
        var pm2 = flow.answerRoute(other, "R18", "F_HARM_THOUGHTS", "YES").step();
        assertEquals("SAFETY_ASSESSMENT", pm2.disposition());
        assertEquals("PM2", pm2.screenCode());
        assertEquals("CRITICAL", pm2.severity());
        assertEquals(clock.instant().plus(Duration.ofHours(336)), store.row(other, "R18").getCooldownUntil(),
            "PM2 keeps the route's own pause");
    }

    @Test
    void notNowInACheckinPausesRouteEighteenForThreeDaysOnly() {
        var start = clock.instant();
        flow.onCheckinPatch(new Trigger(USER, "s1", "POSTPARTUM", List.of(new ItemAnswer("PP18", 2)),
            Map.of("PP18", 2), false, List.of()));
        var steps = flow.skip(USER, "s1", "R18");
        var r18 = steps.stream().filter(s -> s.routeId().equals("R18")).findFirst().orElseThrow();
        assertEquals("NOT_ANSWERED", r18.disposition());
        assertEquals(start.plus(Duration.ofHours(72)), pausedUntil());

        clock.advance(Duration.ofHours(71));
        flow.onCheckinPatch(new Trigger(USER, "s2", "POSTPARTUM", List.of(new ItemAnswer("PP18", 2)),
            Map.of("PP18", 2), false, List.of()));
        assertNotEquals("s2", store.row(USER, "R18").getSessionId(), "still paused at 71 hours");

        clock.advance(Duration.ofHours(2));
        flow.onCheckinPatch(new Trigger(USER, "s3", "POSTPARTUM", List.of(new ItemAnswer("PP18", 2)),
            Map.of("PP18", 2), false, List.of()));
        assertEquals("s3", store.row(USER, "R18").getSessionId(), "R18 opens again after 3 days");
        assertEquals("F_HARM_THOUGHTS", flow.sessionSteps(USER, "s3").stream()
            .filter(s -> s.routeId().equals("R18")).findFirst().orElseThrow().nextFactCode());
    }

    @Test
    void thePm1VariantIsPm1WithTheUrgentHelpLinkAndPm1ItselfIsUnchanged() {
        assertEquals("medical-safety-v2.3", MedicalSafetyCatalog.VERSION);
        var screens = CATALOG.publicScreens("POSTPARTUM").path("screens");
        com.fasterxml.jackson.databind.JsonNode pm1 = null, variant = null;
        for (var s : screens) {
            if ("PM1".equals(s.path("code").asText())) pm1 = s.path("fields");
            if ("PM1-H".equals(s.path("code").asText())) variant = s.path("fields");
        }
        assertNotNull(pm1);
        assertNotNull(variant);
        assertTrue(pm1.path("urgentHelpLink").isMissingNode(), "PM1 for everyone else is unchanged");
        for (var field : List.of("overline", "title", "body", "primary", "secondary", "footnote")) {
            assertEquals(pm1.path(field), variant.path(field), field);
        }
        assertEquals("Not feeling safe right now? How to get urgent help",
            variant.path("urgentHelpLink").path("enUs").asText());
        assertEquals("Сейчас вам небезопасно? Как получить срочную помощь",
            variant.path("urgentHelpLink").path("ru").asText());
    }
}
