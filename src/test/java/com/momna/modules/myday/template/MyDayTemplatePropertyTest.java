package com.momna.modules.myday.template;

import static org.junit.jupiter.api.Assertions.*;

import com.momna.modules.myday.application.template.MyDayCalendarFacts;
import com.momna.modules.myday.application.template.MyDayFacts.Observation;
import com.momna.modules.myday.application.template.MyDayFacts.OnboardingFact;
import com.momna.modules.myday.application.template.MyDayFacts.SafetyProjection;
import com.momna.modules.myday.application.template.MyDayOverlayAdapter;
import com.momna.modules.myday.application.template.MyDayTemplateKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/** Invariants of MOMNA-1324 (overlay adapter) and MOMNA-1325 (template key and calendar facts). */
class MyDayTemplatePropertyTest {
    static final List<String> MODES = List.copyOf(new TreeSet<>(MyDayTemplateKey.LIFE_MODES));
    static final List<String> ITEMS = List.of("M2", "M6", "M7", "M8", "M11", "M12", "C2", "C3", "C4", "C5", "C7",
        "C8", "C9", "C11", "C12", "C13", "T1", "T4", "T8", "P2", "P3", "P15", "P18", "PM1", "PM2", "PM4", "PM5",
        "PM6", "PM9", "PM10", "PM11", "MN1", "MN3", "MN4", "MN7", "MN8", "MN13");
    static final List<String> ROUTES = List.of("MENARCHE_BLEEDING", "CYCLE_BLEEDING", "PREGNANCY_HEADACHE",
        "POSTPARTUM_BLEEDING", "MENOPAUSE_UROGENITAL");
    static final List<String> SEVERITIES = List.of("NONE", "LOW", "MEDIUM", "HIGH", "CRITICAL");

    record Day(String mode, List<Observation> observations, List<SafetyProjection> safety, int lmpDaysAgo) {}

    @Provide
    Arbitrary<Day> days() {
        var observation = Combinators.combine(
            Arbitraries.of("MORNING", "EVENING"), Arbitraries.of(ITEMS), Arbitraries.integers().between(0, 2))
            .as((phase, item, state) -> new Observation(phase, item, state,
                "MORNING".equals(phase) ? new BigDecimal("0.700000") : new BigDecimal("0.300000")));
        var safety = Combinators.combine(Arbitraries.of(ROUTES), Arbitraries.of(SEVERITIES))
            .as((route, severity) -> new SafetyProjection("MORNING", route, severity, "v"));
        return Combinators.combine(Arbitraries.of(MODES), observation.list().ofMaxSize(25), safety.list().ofMaxSize(2),
            Arbitraries.integers().between(-3, 60)).as(Day::new);
    }

    static final LocalDate DAY = LocalDate.parse("2026-11-23");

    static MyDayOverlayAdapter.Input input(Day day, List<Observation> observations) {
        Map<String, OnboardingFact> onboarding = Map.of(
            "onboarding.cycle.lmp", fact(DAY.minusDays(day.lmpDaysAgo()).toString()),
            "onboarding.cycle.regularity", fact("around_expected"),
            "onboarding.planning.lmp", fact(DAY.minusDays(day.lmpDaysAgo()).toString()),
            "onboarding.planning.regularity", fact("fairly_predictable"),
            "onboarding.pregnancy.partner", fact("yes_live_together"),
            "onboarding.perimenopause.occupation", fact("work"));
        var calendar = MyDayCalendarFacts.derive(day.mode(), onboarding, DAY, DAY.minusDays(10), ZoneOffset.UTC);
        return new MyDayOverlayAdapter.Input(day.mode(), DAY, observations, day.safety(), onboarding, calendar);
    }

    static OnboardingFact fact(Object value) {
        return new OnboardingFact(Map.of("value", value), Instant.parse("2026-11-01T10:00:00Z"));
    }

    /** Same facts, any order: the same sorted keys, within the life mode's own catalog and the daily caps. */
    @Property(tries = 400, seed = "20261123")
    void theAdapterIsDeterministicOrderFreeAndBounded(@ForAll("days") Day day, @ForAll Random random) {
        var result = MyDayOverlayAdapter.compute(input(day, day.observations()));
        var shuffled = new ArrayList<>(day.observations());
        Collections.shuffle(shuffled, random);
        assertEquals(result, MyDayOverlayAdapter.compute(input(day, shuffled)));
        assertEquals(new TreeSet<>(result.overlayKeys()).stream().toList(), result.overlayKeys());
        assertTrue(MyDayOverlayAdapter.keys(day.mode()).containsAll(result.overlayKeys()));
        var medical = MyDayOverlayAdapter.CATALOG.stream()
            .filter(e -> e.group() == MyDayOverlayAdapter.Group.MEDICAL).map(e -> e.key()).toList();
        var nonMedical = result.overlayKeys().stream().filter(k -> !medical.contains(k)).count();
        assertTrue(nonMedical <= MyDayOverlayAdapter.MAX_STATE_KEYS + MyDayOverlayAdapter.MAX_FACT_KEYS);
        assertFalse(result.overlayKeys().contains("post_ppd_screen"));
        // a valid template key can always carry the result
        assertDoesNotThrow(() -> key(day.mode(), result.overlayKeys(), "en"));
    }

    /** A stronger answer never takes a key away: every rule is monotone in the answered states. */
    @Property(tries = 400, seed = "20261124")
    void raisingAnAnswerNeverRemovesACandidate(@ForAll("days") Day day, @ForAll @IntRange(min = 0, max = 24) int index) {
        Assume.that(!day.observations().isEmpty());
        var before = MyDayOverlayAdapter.candidates(input(day, day.observations()));
        var raised = new ArrayList<>(day.observations());
        var o = raised.get(index % raised.size());
        raised.set(index % raised.size(), new Observation(o.phase(), o.itemCode(), Math.min(2, o.statePoints() + 1),
            o.phaseShare()));
        var after = MyDayOverlayAdapter.candidates(input(day, raised));
        assertTrue(after.containsAll(before), before + " -> " + after);
    }

    /** One mild answer, whatever it is, never turns a Check-in key on by itself. */
    @Property(tries = 300, seed = "20261125")
    void aSingleMildAnswerNeverActivatesAKey(@ForAll("days") Day day, @ForAll("mildOnly") List<Observation> mild) {
        var stateKeys = MyDayOverlayAdapter.catalog(day.mode()).stream()
            .filter(e -> e.rule() instanceof MyDayOverlayAdapter.Checkin).map(e -> e.key()).toList();
        var candidates = MyDayOverlayAdapter.candidates(input(day, mild));
        stateKeys.forEach(k -> assertFalse(candidates.contains(k), k));
    }

    @Provide
    Arbitrary<List<Observation>> mildOnly() {
        return Combinators.combine(Arbitraries.of("MORNING", "EVENING"), Arbitraries.of(ITEMS))
            .as((phase, item) -> new Observation(phase, item, 1, BigDecimal.ONE)).list().ofMaxSize(20);
    }

    // ---------------------------------------------------------------- template key

    record KeyInput(String mode, int dayNumber, String cycleState, Integer week, String trimester, List<String> overlays,
                    String language) {}

    @Provide
    Arbitrary<KeyInput> keys() {
        return Arbitraries.of(MODES).flatMap(mode -> {
            var overlays = Arbitraries.of(List.copyOf(MyDayOverlayAdapter.keys(mode))).set().ofMaxSize(4)
                .map(s -> (List<String>) new ArrayList<>(s));
            return Combinators.combine(Arbitraries.integers().between(1, 4000),
                Arbitraries.of(List.copyOf(MyDayTemplateKey.CYCLE_STATES)),
                Arbitraries.integers().between(1, 42).injectNull(0.3),
                Arbitraries.of("trimester_1", "trimester_2", "trimester_3").injectNull(0.5),
                overlays, Arbitraries.of("ru", "en"))
                .as((day, cycle, week, trimester, keys, lang) -> new KeyInput(mode, day, cycle, week, trimester, keys, lang));
        });
    }

    static MyDayTemplateKey key(KeyInput k, List<String> overlays, MyDayTemplateKey.Versions versions) {
        return new MyDayTemplateKey(k.mode(), k.dayNumber(), k.cycleState(), "code_" + k.cycleState(), k.week(),
            k.trimester(), overlays, k.language(), versions);
    }

    static MyDayTemplateKey key(String mode, List<String> overlays, String language) {
        return new MyDayTemplateKey(mode, 1, "uncertain", "x_uncertain", null, null, overlays, language,
            MyDayTemplateKey.Versions.current());
    }

    /** Deterministic, and the order of the overlay keys does not matter. */
    @Property(tries = 500, seed = "20261126")
    void theFingerprintIsDeterministicAndIgnoresOverlayOrder(@ForAll("keys") KeyInput k, @ForAll Random random) {
        var shuffled = new ArrayList<>(k.overlays());
        Collections.shuffle(shuffled, random);
        var versions = MyDayTemplateKey.Versions.current();
        assertEquals(key(k, k.overlays(), versions).fingerprint(), key(k, shuffled, versions).fingerprint());
        assertTrue(key(k, k.overlays(), versions).fingerprint().matches("[0-9a-f]{64}"));
    }

    /** Different values of any input, including phase_code and trimester, or of any version give different keys. */
    @Property(tries = 500, seed = "20261127")
    void anyChangedInputOrVersionIsADifferentKey(@ForAll("keys") KeyInput a, @ForAll("keys") KeyInput b) {
        var versions = MyDayTemplateKey.Versions.current();
        var first = key(a, a.overlays(), versions);
        var second = key(b, b.overlays(), versions);
        assertEquals(first.canonical().equals(second.canonical()), first.fingerprint().equals(second.fingerprint()));
        assertEquals(first.equals(second), first.fingerprint().equals(second.fingerprint()));
        var changes = List.of(
            new MyDayTemplateKey.Versions(versions.prompts() + ".next", versions.validator(), versions.overlayAdapter(),
                versions.calendarFacts(), versions.contentRules()),
            new MyDayTemplateKey.Versions(versions.prompts(), versions.validator() + ".next", versions.overlayAdapter(),
                versions.calendarFacts(), versions.contentRules()),
            new MyDayTemplateKey.Versions(versions.prompts(), versions.validator(), versions.overlayAdapter() + ".next",
                versions.calendarFacts(), versions.contentRules()),
            new MyDayTemplateKey.Versions(versions.prompts(), versions.validator(), versions.overlayAdapter(),
                versions.calendarFacts() + ".next", versions.contentRules()),
            new MyDayTemplateKey.Versions(versions.prompts(), versions.validator(), versions.overlayAdapter(),
                versions.calendarFacts(), versions.contentRules() + ".next"));
        for (var changed : changes) {
            assertNotEquals(first.fingerprint(), key(a, a.overlays(), changed).fingerprint());
        }
        var otherPhase = new MyDayTemplateKey(a.mode(), a.dayNumber(), a.cycleState(), "other_code", a.week(),
            a.trimester(), a.overlays(), a.language(), versions);
        assertNotEquals(first.fingerprint(), otherPhase.fingerprint());
        var otherTrimester = new MyDayTemplateKey(a.mode(), a.dayNumber(), a.cycleState(), "code_" + a.cycleState(),
            a.week(), a.trimester() == null ? "trimester_1" : null, a.overlays(), a.language(), versions);
        assertNotEquals(first.fingerprint(), otherTrimester.fingerprint());
    }

    /** Personal or free values cannot be represented: anything outside the vocabularies is rejected. */
    @Property(tries = 300, seed = "20261128")
    void freeTextNeverFitsIntoTheKey(@ForAll("personal") String value) {
        assertThrows(IllegalArgumentException.class, () -> new MyDayTemplateKey(value, 1, "uncertain", "x", null, null,
            List.of(), "en", MyDayTemplateKey.Versions.current()));
        assertThrows(IllegalArgumentException.class, () -> new MyDayTemplateKey("adult_cycle", 1, value, "x", null, null,
            List.of(), "en", MyDayTemplateKey.Versions.current()));
        assertThrows(IllegalArgumentException.class, () -> new MyDayTemplateKey("adult_cycle", 1, "uncertain", value,
            null, null, List.of(), "en", MyDayTemplateKey.Versions.current()));
        assertThrows(IllegalArgumentException.class, () -> new MyDayTemplateKey("adult_cycle", 1, "uncertain", "x",
            null, null, List.of(value), "en", MyDayTemplateKey.Versions.current()));
        assertThrows(IllegalArgumentException.class, () -> new MyDayTemplateKey("adult_cycle", 1, "uncertain", "x",
            null, null, List.of(), value, MyDayTemplateKey.Versions.current()));
    }

    @Provide
    Arbitrary<String> personal() {
        return Arbitraries.oneOf(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12).map(s -> s + "@example.test"),
            Arbitraries.create(() -> UUID.randomUUID().toString()),
            Arbitraries.strings().withChars("Аня Мама боль сильная ").ofMinLength(1).ofMaxLength(30),
            Arbitraries.strings().withChars("Ann Smith | = , ").ofMinLength(3).ofMaxLength(30))
            .filter(s -> !s.matches("[a-z][a-z0-9_]*"));
    }

    // ---------------------------------------------------------------- calendar facts

    @Property(tries = 400, seed = "20261129")
    void pregnancyWeekAndTrimesterAgree(@ForAll @IntRange(min = -10, max = 320) int daysToDue) {
        var onboarding = Map.of("onboarding.pregnancy.edd", fact(DAY.plusDays(daysToDue).toString()));
        var facts = MyDayCalendarFacts.derive("pregnancy", onboarding, DAY, DAY.minusDays(5), ZoneOffset.UTC);
        long gestation = 280 - daysToDue;
        if (gestation < 0 || gestation > 44 * 7) {
            assertNull(facts.weekNumber());
            assertNull(facts.trimester());
            assertEquals("preg_unknown", facts.phaseCode());
            return;
        }
        int week = facts.weekNumber();
        assertTrue(week >= 1 && week <= 42);
        assertEquals(Math.max(1, Math.min(42, gestation / 7)), week);
        assertEquals(week < 14 ? "trimester_1" : week < 28 ? "trimester_2" : "trimester_3", facts.trimester());
        assertEquals("not_applicable", facts.cycleState());
        assertEquals("preg_" + facts.trimester().replace("_", "") + "_w" + week, facts.phaseCode());
        assertDoesNotThrow(() -> new MyDayTemplateKey("pregnancy", facts.dayNumber(), facts.cycleState(),
            facts.phaseCode(), facts.weekNumber(), facts.trimester(), List.of(), "ru", MyDayTemplateKey.Versions.current()));
    }

    @Property(tries = 200, seed = "20261130")
    void theCycleStateFollowsTheCycleDay(@ForAll @IntRange(min = -5, max = 60) int daysAgo, @ForAll boolean regular) {
        var onboarding = Map.of("onboarding.cycle.lmp", fact(DAY.minusDays(daysAgo).toString()),
            "onboarding.cycle.regularity", fact(regular ? "around_expected" : "every_cycle_different"));
        var state = MyDayCalendarFacts.derive("adult_cycle", onboarding, DAY, DAY, ZoneOffset.UTC).cycleState();
        int cycleDay = daysAgo + 1;
        String expected;
        if (cycleDay < 1) expected = "uncertain";
        else if (cycleDay <= 5) expected = "menstrual";
        else if (!regular) expected = "uncertain";
        else if (cycleDay <= 7) expected = "follicular";
        else if (cycleDay <= 19) expected = "fertile_window";
        else if (cycleDay <= 32) expected = "luteal";
        else expected = "uncertain";
        assertEquals(expected, state);
        assertFalse("ovulation_peak".equals(state), "ovulation is never guessed");
    }
}
