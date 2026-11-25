package com.momna.platform.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.safety.CheckinMedicalSafety;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Invariants of the v2.2 resolver (MOMNA-1145 acceptance 5): every route ends, no fact is asked twice, an
 * emergency YES is never downgraded, an all-NO run never alarms, and an unsure urgent sign never ends safe.
 */
class MedicalSafetyPropertyTest {
    private static final MedicalSafetyCatalog CATALOG = new MedicalSafetyCatalog(new ObjectMapper());
    private static final MedicalSafetyResolver RESOLVER = new MedicalSafetyResolver();
    private static final List<String> ROUTES = CATALOG.routes().stream().map(MedicalSafetyCatalog.Route::routeId)
        .toList();
    private static final List<String> PERIODS = List.of("MENARCHE", "CYCLE", "PLANNING", "PREGNANCY", "POSTPARTUM",
        "PERIMENOPAUSE", "MENOPAUSE");
    private static final Set<String> DISPOSITIONS = Set.of("EMERGENCY", "SAFETY_ASSESSMENT", "URGENT", "PROMPT",
        "ROUTINE", "UNKNOWN", "NOT_REPORTED");

    record Run(MedicalStep last, Map<String, String> answers, int asked) {}

    /** Answers whatever the resolver asks, in order, from the scripted values; QM and BP1 included. */
    private static Run run(String routeId, String period, List<String> script, List<Integer> pressure) {
        var route = CATALOG.route(routeId);
        var answers = new LinkedHashMap<String, String>();
        var asked = 0;
        var step = RESOLVER.evaluateMedical(route, CATALOG, answers, Map.of(), false, period);
        while (step.question()) {
            assertTrue(asked < 40, routeId + " never ends");
            if ("QM".equals(step.screenCode())) {
                for (var fact : step.questionFactCodes()) {
                    assertFalse(answers.containsKey(fact), "QM shows an answered fact " + fact);
                    answers.put(fact, script.get(asked++ % script.size()));
                }
            } else if ("BP_READING".equals(step.nextFactCode())) {
                assertFalse(answers.containsKey("BP_READING"), "BP1 asked twice");
                var systolic = pressure.get(0);
                var diastolic = Math.min(pressure.get(1), systolic - 1);
                answers.put("BP_READING", systolic + "/" + diastolic);
                asked++;
            } else {
                var fact = step.nextFactCode();
                assertFalse(answers.containsKey(fact), routeId + " asked " + fact + " twice");
                answers.put(fact, script.get(asked++ % script.size()));
            }
            step = RESOLVER.evaluateMedical(route, CATALOG, answers, Map.of(), false, period);
        }
        return new Run(step, Map.copyOf(answers), asked);
    }

    @Provide
    Arbitrary<String> routes() { return Arbitraries.of(ROUTES); }

    @Provide
    Arbitrary<String> periods() { return Arbitraries.of(PERIODS); }

    @Provide
    Arbitrary<List<String>> scripts() {
        return Arbitraries.of("YES", "NO", "UNSURE", "ABSENT").list().ofMinSize(1).ofMaxSize(12);
    }

    @Provide
    Arbitrary<List<Integer>> pressures() {
        return Arbitraries.integers().between(60, 220).list().ofSize(2);
    }

    @Property(tries = 1500)
    void everyRunEndsInAKnownOutcomeWithoutAskingAFactTwice(
        @ForAll("routes") String routeId, @ForAll("periods") String period,
        @ForAll("scripts") List<String> script, @ForAll("pressures") List<Integer> pressure
    ) {
        var run = run(routeId, period, script, pressure);
        assertEquals("COMPLETE", run.last().state());
        assertTrue(DISPOSITIONS.contains(run.last().disposition()), run.last().disposition());
        assertEquals(routeId, run.last().routeId());
        assertEquals("UNKNOWN".equals(run.last().disposition()),
            "UNKNOWN".equals(CheckinMedicalSafety.severity(run.last())),
            "a completed route projects to UNKNOWN severity exactly when its outcome is UNKNOWN");
    }

    @Property(tries = 1500)
    void anEmergencyYesIsNeverDowngraded(
        @ForAll("routes") String routeId, @ForAll("periods") String period,
        @ForAll("scripts") List<String> script, @ForAll("pressures") List<Integer> pressure
    ) {
        var run = run(routeId, period, script, pressure);
        boolean emergencyYes = run.answers().entrySet().stream()
            .anyMatch(e -> "YES".equals(e.getValue()) && "EMERGENCY".equals(role(e.getKey())));
        if (emergencyYes) {
            assertEquals("EMERGENCY", run.last().disposition(), routeId + " " + run.answers());
            assertEquals("CRITICAL", CheckinMedicalSafety.severity(run.last()));
        }
        boolean harm = "YES".equals(run.answers().get("F_HARM_THOUGHTS"));
        if (harm && !emergencyYes) assertEquals("SAFETY_ASSESSMENT", run.last().disposition());
    }

    @Property(tries = 1500)
    void anUrgentYesNeverEndsBelowUrgent(
        @ForAll("routes") String routeId, @ForAll("periods") String period,
        @ForAll("scripts") List<String> script, @ForAll("pressures") List<Integer> pressure
    ) {
        var run = run(routeId, period, script, pressure);
        boolean urgentYes = run.answers().entrySet().stream()
            .anyMatch(e -> "YES".equals(e.getValue()) && "URGENT".equals(role(e.getKey())));
        if (urgentYes) {
            assertTrue(Set.of("EMERGENCY", "SAFETY_ASSESSMENT", "URGENT").contains(run.last().disposition()),
                routeId + " " + run.answers() + " -> " + run.last().disposition());
        }
    }

    @Property(tries = 800)
    void allNoNeverAlarmsAndAnUnsureUrgentSignNeverEndsSafe(
        @ForAll("routes") String routeId, @ForAll("periods") String period, @ForAll("pressures") List<Integer> pressure
    ) {
        var normal = List.of(110, 70);
        var allNo = run(routeId, period, List.of("NO"), normal);
        assertEquals("NOT_REPORTED", allNo.last().disposition(), routeId + " " + allNo.answers());
        assertEquals("NONE", CheckinMedicalSafety.severity(allNo.last()));

        var unsure = run(routeId, period, List.of("UNSURE"), pressure);
        boolean unsureUrgent = unsure.answers().entrySet().stream()
            .anyMatch(e -> "UNSURE".equals(e.getValue()) && Set.of("URGENT", "EMERGENCY").contains(role(e.getKey())));
        if (unsureUrgent) assertNotEquals("NOT_REPORTED", unsure.last().disposition(), routeId + " " + unsure.answers());
    }

    private static String role(String factCode) {
        if ("BP_READING".equals(factCode)) return "INPUT";
        return CATALOG.fact(factCode).role();
    }
}
