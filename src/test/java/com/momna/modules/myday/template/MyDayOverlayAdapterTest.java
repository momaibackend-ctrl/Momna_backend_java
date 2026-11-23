package com.momna.modules.myday.template;

import static org.junit.jupiter.api.Assertions.*;

import com.momna.modules.myday.application.template.MyDayCalendarFacts;
import com.momna.modules.myday.application.template.MyDayFacts.Observation;
import com.momna.modules.myday.application.template.MyDayFacts.OnboardingFact;
import com.momna.modules.myday.application.template.MyDayFacts.SafetyProjection;
import com.momna.modules.myday.application.template.MyDayOverlayAdapter;
import com.momna.modules.myday.application.template.MyDayOverlayAdapter.Group;
import com.momna.modules.myday.application.template.MyDayOverlayAdapter.SourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Golden cases of MYDAY-SPEC-02 (MOMNA-1324): for every mappable key an input that turns it on and one that does not. */
class MyDayOverlayAdapterTest {
    static final LocalDate MONDAY = LocalDate.parse("2026-11-23");
    static final LocalDate SATURDAY = LocalDate.parse("2026-11-28");
    static final BigDecimal FULL = new BigDecimal("1.000000");
    static final BigDecimal MORNING_SHARE = new BigDecimal("0.700000");
    static final BigDecimal EVENING_SHARE = new BigDecimal("0.300000");

    /** The 51 + 1 keys named by the prompts v1.1 (postpartum has 9: the canon's "51" miscounts it as 8). */
    static final Map<String, List<String>> SPEC_KEYS = Map.of(
        "teen_menarche", List.of("teen_bleeding_now", "teen_pain_high", "teen_school_day", "teen_no_trusted_adult",
            "teen_privacy_high", "teen_red_flag", "teen_mood_sensitive"),
        "adult_cycle", List.of("adult_heavy_flow", "adult_pain_high", "adult_pms", "adult_work_stress",
            "adult_relationship_tension", "adult_low_energy", "adult_red_flag"),
        "planning", List.of("planning_fertile_window", "planning_cycle_irregular", "planning_partner_stress",
            "planning_anxiety_high", "planning_long_wait", "planning_health_check"),
        "pregnancy", List.of("preg_nausea", "preg_fatigue", "preg_anxiety", "preg_high_risk", "preg_partner_present",
            "preg_partner_absent", "preg_work_active", "preg_third_trimester", "preg_first_pregnancy"),
        "postpartum", List.of("post_birth_week_1_4", "post_breastfeeding", "post_no_partner", "post_partner_present",
            "post_c_section", "post_ppd_screen", "post_returning_work", "post_intimacy_ready", "post_low_milk"),
        "perimenopause", List.of("peri_hot_flashes", "peri_sleep_disrupted", "peri_mood_shift",
            "peri_cycle_irregular", "peri_brain_fog", "peri_low_libido", "peri_work_challenge"),
        "menopause", List.of("meno_sleep_poor", "meno_hot_flashes", "meno_joint_pain", "meno_vaginal_dryness",
            "meno_mood_low", "meno_active_work", "meno_new_chapter"));

    @Test
    void theTableCoversExactlyTheKeysOfThePromptsAndEveryKeyHasASourceOrAReason() {
        for (var mode : SPEC_KEYS.keySet()) {
            assertEquals(new TreeSet<>(SPEC_KEYS.get(mode)), MyDayOverlayAdapter.keys(mode), mode);
        }
        assertEquals(52, MyDayOverlayAdapter.CATALOG.size());
        var notMappable = MyDayOverlayAdapter.CATALOG.stream()
            .filter(e -> e.sourceType() == SourceType.NOT_MAPPABLE).map(e -> e.key()).collect(Collectors.toSet());
        assertEquals(Set.of("adult_relationship_tension", "post_ppd_screen", "meno_new_chapter"), notMappable);
        for (var entry : MyDayOverlayAdapter.CATALOG) {
            assertFalse(entry.meaning().isBlank(), entry.key());
            assertFalse(entry.owner().isBlank(), entry.key());
        }
    }

    @Test
    void medicalKeysComeOnlyFromTheSafetyDecision() {
        var medical = MyDayOverlayAdapter.CATALOG.stream().filter(e -> e.group() == Group.MEDICAL)
            .map(e -> e.key()).collect(Collectors.toSet());
        assertEquals(Set.of("teen_red_flag", "adult_red_flag", "preg_high_risk"), medical);
        MyDayOverlayAdapter.CATALOG.stream().filter(e -> e.group() == Group.MEDICAL).forEach(e -> {
            assertEquals(SourceType.SAFETY_PROJECTION, e.sourceType(), e.key());
            assertInstanceOf(MyDayOverlayAdapter.Safety.class, e.rule(), e.key());
        });
    }

    // ---------------------------------------------------------------- weights

    @Test
    void aKeyFollowsTheWeightedShareOfTheDaysAnswersNotASingleOne() {
        // Only Morning(D): the strongest state turns the key on, a mild one does not.
        assertOn("adult_pain_high", cycle(m("C3", 2)));
        assertOff("adult_pain_high", cycle(m("C3", 1)));
        // Morning(D) 70% + Evening(D-1) 30%.
        assertOn("adult_pain_high", cycle(m2("C3", 2), e2("C13", 0)));     // 0.70
        assertOff("adult_pain_high", cycle(m2("C3", 0), e2("C13", 2)));    // 0.30: the morning says it passed
        assertOn("adult_pain_high", cycle(m2("C3", 1), e2("C13", 2)));     // 0.35 + 0.30 = 0.65
        assertOff("adult_pain_high", cycle(m2("C3", 1), e2("C13", 1)));    // 0.50
        // Only Evening(D-1): its share is the whole day.
        assertOn("adult_pain_high", cycle(e("C13", 2)));
        assertOff("adult_pain_high", cycle(e("C13", 1)));
        // An unanswered item is not an answer: only the evening spoke about pain.
        assertOn("adult_pain_high", cycle(m2("C2", 0), e2("C13", 2)));
        // The strongest of the key's items in a phase counts once, not twice.
        assertOff("adult_pain_high", cycle(m2("C3", 1), m2("C13", 1)));
    }

    @Test
    void withoutACheckinOnlyOnboardingAndCalendarKeysCanTurnOn() {
        var onboarding = Map.of(
            "onboarding.pregnancy.edd", fact(MONDAY.plusDays(40).toString()),
            "onboarding.pregnancy.partner", fact("yes_live_together"),
            "onboarding.pregnancy.preg_history", fact(List.of("first_pregnancy")));
        var result = MyDayOverlayAdapter.compute(input("pregnancy", MONDAY, List.of(), List.of(), onboarding));
        assertEquals(List.of("preg_first_pregnancy", "preg_partner_present", "preg_third_trimester"), result.overlayKeys());
    }

    // ---------------------------------------------------------------- safety

    @Test
    void aRedFlagNeedsAMediumOrHigherDecisionOnThePeriodsOwnRoute() {
        assertOn("adult_red_flag", safety("adult_cycle", "CYCLE_BLEEDING", "MEDIUM"));
        assertOn("adult_red_flag", safety("adult_cycle", "CYCLE_BLEEDING", "CRITICAL"));
        assertOff("adult_red_flag", safety("adult_cycle", "CYCLE_BLEEDING", "LOW"));
        assertOff("adult_red_flag", safety("adult_cycle", "PREGNANCY_HEADACHE", "HIGH"));
        // Strong answers alone never make a medical key.
        assertOff("adult_red_flag", cycle(m("C3", 2), m("C4", 2), m("C6", 2)));
        assertOn("teen_red_flag", safety("teen_menarche", "MENARCHE_DIZZINESS", "HIGH"));
        assertOff("teen_red_flag", input("teen_menarche", MONDAY, List.of(m("M4", 2), m("M5", 2)), List.of(), Map.of()));
        assertOn("preg_high_risk", safety("pregnancy", "PREGNANCY_SWELLING", "HIGH"));
        assertOff("preg_high_risk", input("pregnancy", MONDAY, List.of(m("P6", 2), m("P8", 2)), List.of(), Map.of()));
    }

    @Test
    void emotionalHeavinessAfterBirthIsNeverAnOverlay() {
        var all = new ArrayList<Observation>();
        for (int i = 1; i <= 18; i++) all.add(m("PP" + i, 2));
        var result = MyDayOverlayAdapter.compute(input("postpartum", MONDAY, all,
            List.of(new SafetyProjection("MORNING", "POSTPARTUM_BLEEDING", "CRITICAL", "v")), Map.of()));
        assertFalse(result.overlayKeys().contains("post_ppd_screen"));
    }

    /** PST_FEEDING (MOMNA-561, Product decision 24.11.2026): all seven codes, the clarification, several babies. */
    @Test
    void breastfeedingFollowsTheSevenFeedingCodesPerBaby() {
        assertEquals(Set.of("nursing_at_breast", "pumped_breast_milk", "combination_feeding", "infant_formula",
            "tube_or_medically_supported", "feeding_plan_changing", "prefer_not_to_say"), MyDayOverlayAdapter.FEEDING_CODES);
        for (var code : MyDayOverlayAdapter.FEEDING_CODES) {
            boolean on = Set.of("nursing_at_breast", "pumped_breast_milk", "combination_feeding").contains(code);
            for (var shape : List.<Object>of(code, Map.of("1", code), List.of(Map.of("childRef", "1", "value", code)))) {
                if (on) assertOn("post_breastfeeding", feeding(shape, null));
                else assertOff("post_breastfeeding", feeding(shape, null));
            }
        }
        // the clarification decides tube/medical feeding and a changing plan: yes on, no off, skipped UNKNOWN off
        for (var code : List.of("tube_or_medically_supported", "feeding_plan_changing")) {
            assertOn("post_breastfeeding", feeding(Map.of("1", code), Map.of("1", "yes")));
            assertOn("post_breastfeeding", feeding(code, "yes"));
            assertOff("post_breastfeeding", feeding(Map.of("1", code), Map.of("1", "no")));
            assertOff("post_breastfeeding", feeding(Map.of("1", code), null));
        }
        // a "yes" about a formula-fed baby does not make breast milk
        assertOff("post_breastfeeding", feeding(Map.of("1", "infant_formula"), Map.of("1", "yes")));
    }

    @Test
    void withSeveralBabiesOneConfirmedBabyIsEnough() {
        assertOn("post_breastfeeding", feeding(Map.of("1", "infant_formula", "2", "nursing_at_breast"), null));
        assertOn("post_breastfeeding", feeding(List.of(Map.of("childRef", "a", "value", "infant_formula"),
            Map.of("childRef", "b", "value", "pumped_breast_milk")), null));
        // the clarification belongs to its own baby
        assertOn("post_breastfeeding", feeding(Map.of("1", "tube_or_medically_supported", "2", "feeding_plan_changing"),
            Map.of("1", "no", "2", "yes")));
        assertOff("post_breastfeeding", feeding(Map.of("1", "tube_or_medically_supported", "2", "infant_formula"),
            Map.of("2", "yes")));
        assertOff("post_breastfeeding", feeding(Map.of("1", "infant_formula", "2", "feeding_plan_changing"),
            Map.of("2", "no")));
        assertOff("post_breastfeeding", feeding(Map.of("1", "infant_formula", "2", "prefer_not_to_say"), null));
    }

    @Test
    void aFeedingDifficultyIsNotProofOfBreastMilk() {
        for (var issue : List.of("latching_trouble", "sore_nipples", "engorgement", "clogged_duct", "pumping_effort",
            "milk_supply_worry")) {
            assertOff("post_breastfeeding", input("postpartum", MONDAY, List.of(), List.of(),
                Map.of("onboarding.postpartum.feeding_issues", fact(List.of(issue)))));
        }
    }

    static MyDayOverlayAdapter.Input feeding(Object answer, Object breastMilkNow) {
        var onboarding = new HashMap<String, OnboardingFact>();
        onboarding.put("onboarding.postpartum.feeding", fact(answer));
        if (breastMilkNow != null) onboarding.put("onboarding.postpartum.feeding_breast_milk_now", fact(breastMilkNow));
        return input("postpartum", MONDAY, List.of(), List.of(), onboarding);
    }

    // ---------------------------------------------------------------- caps

    @Test
    void atMostThreeCheckinKeysStrongestFirstAndThreeFactKeysButEveryMedicalKey() {
        var observations = List.of(m2("C3", 2), m2("C4", 2), m2("C2", 2), m2("C8", 1), e2("C11", 2), m2("C7", 2));
        var result = MyDayOverlayAdapter.compute(input("adult_cycle", MONDAY, observations,
            List.of(new SafetyProjection("MORNING", "CYCLE_BLEEDING", "HIGH", "v")), Map.of()));
        // pain 1.0, heavy flow 1.0, low energy 1.0 beat work stress 0.65.
        assertEquals(List.of("adult_heavy_flow", "adult_low_energy", "adult_pain_high", "adult_red_flag"),
            result.overlayKeys());
        var workStress = result.activations().stream().filter(a -> a.key().equals("adult_work_stress")).findFirst().orElseThrow();
        assertFalse(workStress.passed(), "a candidate beyond the cap is recorded, not passed on");

        var facts = Map.of(
            "onboarding.postpartum.birth_date", fact(MONDAY.minusDays(10).toString()),
            "onboarding.postpartum.delivery", fact("emergency_c_section"),
            "onboarding.postpartum.feeding_issues", fact(Map.of("milk_supply_worry", true)),
            "onboarding.postpartum.partner", fact("live_together"),
            "onboarding.postpartum.work", fact("need_return_soon"));
        var post = MyDayOverlayAdapter.compute(input("postpartum", MONDAY, List.of(), List.of(), facts));
        assertEquals(List.of("post_birth_week_1_4", "post_c_section", "post_low_milk"), post.overlayKeys());
    }

    // ---------------------------------------------------------------- every mappable key, on and off

    @Test
    void everyMappableKeyHasAnInputThatTurnsItOnAndOneThatDoesNot() {
        var covered = new TreeSet<String>();
        for (var c : cases()) {
            assertOn(c.key(), c.on());
            assertOff(c.key(), c.off());
            covered.add(c.key());
        }
        var mappable = MyDayOverlayAdapter.CATALOG.stream()
            .filter(e -> e.sourceType() != SourceType.NOT_MAPPABLE).map(e -> e.key()).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(mappable, covered);
    }

    record Case(String key, MyDayOverlayAdapter.Input on, MyDayOverlayAdapter.Input off) {}

    static List<Case> cases() {
        var out = new ArrayList<Case>();
        // Check-in keys with one item rule: strongest vs mild answer.
        for (var entry : MyDayOverlayAdapter.CATALOG) {
            if (entry.rule() instanceof MyDayOverlayAdapter.Checkin checkin) {
                var item = checkin.items().getFirst();
                out.add(new Case(entry.key(), input(entry.lifeMode(), MONDAY, List.of(m(item, 2)), List.of(), Map.of()),
                    input(entry.lifeMode(), MONDAY, List.of(m(item, 1)), List.of(), Map.of())));
            }
            if (entry.rule() instanceof MyDayOverlayAdapter.Safety safety) {
                out.add(new Case(entry.key(), safety(entry.lifeMode(), safety.routePrefix() + "X", "HIGH"),
                    safety(entry.lifeMode(), safety.routePrefix() + "X", "LOW")));
            }
        }
        // teen_menarche
        out.add(onboardingCase("teen_bleeding_now", "teen_menarche", "onboarding.menarche.last_period",
            MONDAY.minusDays(2).toString(), MONDAY.minusDays(8).toString()));
        out.add(new Case("teen_school_day",
            input("teen_menarche", MONDAY, List.of(), List.of(), Map.of("onboarding.menarche.day_context", fact("school"))),
            input("teen_menarche", SATURDAY, List.of(), List.of(), Map.of("onboarding.menarche.day_context", fact("school")))));
        out.add(new Case("teen_school_day",
            input("teen_menarche", SATURDAY, List.of(m("M6", 0)), List.of(), Map.of()),
            input("teen_menarche", SATURDAY, List.of(e("M6", 2)), List.of(), Map.of())));
        out.add(onboardingCase("teen_no_trusted_adult", "teen_menarche", "onboarding.menarche.trusted_adult",
            "no_one", "someone_unsure"));
        out.add(onboardingCase("teen_privacy_high", "teen_menarche", "onboarding.menarche.notifications",
            "neutral_wording", "direct_ok"));
        out.add(new Case("teen_privacy_high",
            input("teen_menarche", MONDAY, List.of(m("M8", 2)), List.of(), Map.of()),
            input("teen_menarche", MONDAY, List.of(m("M8", 1)), List.of(), Map.of())));
        // adult_cycle: PMS needs the luteal days and symptoms
        var luteal = Map.of("onboarding.cycle.lmp", fact(MONDAY.minusDays(24).toString()),
            "onboarding.cycle.regularity", fact("around_expected"));
        var follicular = Map.of("onboarding.cycle.lmp", fact(MONDAY.minusDays(9).toString()),
            "onboarding.cycle.regularity", fact("around_expected"));
        out.add(new Case("adult_pms", input("adult_cycle", MONDAY, List.of(m("C7", 2)), List.of(), luteal),
            input("adult_cycle", MONDAY, List.of(m("C7", 2)), List.of(), follicular)));
        // planning
        out.add(new Case("planning_fertile_window",
            input("planning", MONDAY, List.of(), List.of(), Map.of("onboarding.planning.lmp", fact(MONDAY.minusDays(10).toString()),
                "onboarding.planning.regularity", fact("fairly_predictable"))),
            input("planning", MONDAY, List.of(), List.of(), Map.of("onboarding.planning.lmp", fact(MONDAY.minusDays(10).toString()),
                "onboarding.planning.regularity", fact("often_irregular")))));
        out.add(onboardingCase("planning_cycle_irregular", "planning", "onboarding.planning.regularity",
            "often_irregular", "fairly_predictable"));
        out.add(onboardingCase("planning_partner_stress", "planning", "onboarding.planning.partner_involvement",
            "tension", "pressured_unsafe"));
        out.add(onboardingCase("planning_long_wait", "planning", "onboarding.planning.duration", "6_12m", "3_6m"));
        out.add(new Case("planning_health_check",
            input("planning", MONDAY, List.of(m("T4", 1)), List.of(), Map.of()),
            input("planning", MONDAY, List.of(m("T4", 0)), List.of(), Map.of())));
        out.add(onboardingCase("planning_health_check", "planning", "onboarding.planning.treatment_expected_date",
            MONDAY.plusDays(7).toString(), MONDAY.plusDays(8).toString()));
        // pregnancy
        out.add(onboardingCase("preg_partner_present", "pregnancy", "onboarding.pregnancy.partner",
            "yes_live_together", "casual_multiple"));
        out.add(onboardingCase("preg_partner_absent", "pregnancy", "onboarding.pregnancy.partner",
            "no_partner", "yes_live_together"));
        out.add(onboardingCase("preg_work_active", "pregnancy", "onboarding.pregnancy.occupation",
            "work", "on_leave_not_working"));
        out.add(onboardingCase("preg_third_trimester", "pregnancy", "onboarding.pregnancy.edd",
            MONDAY.plusDays(84).toString(), MONDAY.plusDays(85).toString()));   // 28w0d vs 27w6d
        out.add(new Case("preg_first_pregnancy",
            input("pregnancy", MONDAY, List.of(), List.of(), Map.of("onboarding.pregnancy.preg_history",
                fact(Map.of("first_pregnancy", true)))),
            input("pregnancy", MONDAY, List.of(), List.of(), Map.of("onboarding.pregnancy.preg_history",
                fact(Map.of("ended_in_birth", true, "first_pregnancy", false))))));
        // postpartum
        out.add(onboardingCase("post_birth_week_1_4", "postpartum", "onboarding.postpartum.birth_date",
            MONDAY.minusDays(27).toString(), MONDAY.minusDays(28).toString()));
        out.add(onboardingCase("post_breastfeeding", "postpartum", "onboarding.postpartum.feeding",
            Map.of("1", "nursing_at_breast"), Map.of("1", "infant_formula")));
        out.add(onboardingCase("post_no_partner", "postpartum", "onboarding.postpartum.partner",
            "no_partner", "live_together"));
        out.add(onboardingCase("post_partner_present", "postpartum", "onboarding.postpartum.partner",
            "live_together", "uncertain_complicated"));
        out.add(onboardingCase("post_c_section", "postpartum", "onboarding.postpartum.delivery",
            "planned_c_section", "vaginal"));
        out.add(onboardingCase("post_returning_work", "postpartum", "onboarding.postpartum.work",
            "in_person", "on_leave"));
        out.add(new Case("post_intimacy_ready",
            input("postpartum", MONDAY, List.of(), List.of(), Map.of("onboarding.postpartum.intimacy_status", fact("sometimes"),
                "onboarding.postpartum.birth_date", fact(MONDAY.minusDays(42).toString()))),     // week 7
            input("postpartum", MONDAY, List.of(), List.of(), Map.of("onboarding.postpartum.intimacy_status", fact("sometimes"),
                "onboarding.postpartum.birth_date", fact(MONDAY.minusDays(41).toString())))));   // week 6
        out.add(onboardingCase("post_low_milk", "postpartum", "onboarding.postpartum.feeding_issues",
            "milk_supply_worry", "sore_nipples"));
        // perimenopause
        out.add(onboardingCase("peri_cycle_irregular", "perimenopause", "onboarding.perimenopause.changes",
            "skip_some", "much_heavier"));
        out.add(onboardingCase("peri_low_libido", "perimenopause", "onboarding.perimenopause.intimacy",
            "desire_decreased", "comfortable"));
        var works = Map.of("onboarding.perimenopause.occupation", fact("work"));
        out.add(new Case("peri_work_challenge",
            input("perimenopause", MONDAY, List.of(m("PM6", 2), m("PM2", 2)), List.of(), works),
            input("perimenopause", MONDAY, List.of(m("PM6", 2), m("PM2", 1)), List.of(), works)));
        // menopause
        out.add(onboardingCase("meno_active_work", "menopause", "onboarding.menopause.occupation", "work", "retired"));
        return out;
    }

    // ---------------------------------------------------------------- helpers

    static Case onboardingCase(String key, String mode, String field, Object on, Object off) {
        return new Case(key, input(mode, MONDAY, List.of(), List.of(), Map.of(field, fact(on))),
            input(mode, MONDAY, List.of(), List.of(), Map.of(field, fact(off))));
    }

    static void assertOn(String key, MyDayOverlayAdapter.Input input) {
        assertTrue(MyDayOverlayAdapter.compute(input).overlayKeys().contains(key), key + " should be on");
    }

    static void assertOff(String key, MyDayOverlayAdapter.Input input) {
        assertFalse(MyDayOverlayAdapter.candidates(input).contains(key), key + " should be off");
    }

    static MyDayOverlayAdapter.Input cycle(Observation... observations) {
        return input("adult_cycle", MONDAY, List.of(observations), List.of(), Map.of());
    }

    static MyDayOverlayAdapter.Input safety(String mode, String route, String severity) {
        return input(mode, MONDAY, List.of(), List.of(new SafetyProjection("MORNING", route, severity, "v")), Map.of());
    }

    static MyDayOverlayAdapter.Input input(String mode, LocalDate day, List<Observation> observations,
                                           List<SafetyProjection> safety, Map<String, OnboardingFact> onboarding) {
        var calendar = MyDayCalendarFacts.derive(mode, onboarding, day, day.minusDays(30), java.time.ZoneOffset.UTC);
        return new MyDayOverlayAdapter.Input(mode, day, observations, safety, onboarding, calendar);
    }

    static OnboardingFact fact(Object value) {
        return new OnboardingFact(Map.of("value", value), Instant.parse("2026-11-01T10:00:00Z"));
    }

    static Observation m(String item, int state) { return new Observation("MORNING", item, state, FULL); }
    static Observation e(String item, int state) { return new Observation("EVENING", item, state, FULL); }
    static Observation m2(String item, int state) { return new Observation("MORNING", item, state, MORNING_SHARE); }
    static Observation e2(String item, int state) { return new Observation("EVENING", item, state, EVENING_SHARE); }
}
