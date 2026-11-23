package com.momna.modules.myday.application.template;

import com.momna.modules.myday.application.template.MyDayFacts.Observation;
import com.momna.modules.myday.application.template.MyDayFacts.OnboardingFact;
import com.momna.modules.myday.application.template.MyDayFacts.SafetyProjection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * MYDAY-SPEC-02 (MOMNA-1324): a deterministic compatibility adapter from facts that already exist
 * (weighted-v3 Check-in answers, onboarding answers, calendar facts, the authoritative SafetyDecision)
 * to the {@code overlay_keys} of the My Day prompts v1.1. It is not a second scoring engine: it never
 * recomputes adjustment or safety scores, it reads their results.
 *
 * <p><b>Weights, not single answers.</b> A Check-in overlay is the share of the day's weighted evidence
 * that says "noticeable". For each phase that answered one of the key's items, take its strongest state
 * (0, 0.5 for the middle state, 1 for the strongest) and weight it by the phase's base share of the day
 * (Morning(D) 70% / Evening(D-1) 30%, or 100% for the only phase). The key turns on at
 * {@value #ACTIVATION_PERCENT}% of the phases that spoke about it. So one mild answer never turns a key
 * on; a strong morning does; a strong evening is outweighed by a morning that says it has passed; an
 * unanswered item is not an answer.
 *
 * <p><b>Medical keys</b> come only from the SafetyDecision of a final source session with severity
 * MEDIUM or higher for the period's own routes, never from a score or from the LLM (MOMNA-1145).
 * {@code post_ppd_screen} stays NOT_MAPPABLE until MOMNA-1146 approves a mental-health route.
 *
 * <p><b>Few overlays a day.</b> At most {@value #MAX_STATE_KEYS} Check-in keys (strongest first) and
 * {@value #MAX_FACT_KEYS} onboarding/calendar keys (catalog priority) are passed on; medical keys always.
 */
public final class MyDayOverlayAdapter {
    public static final String VERSION = "myday.overlay-adapter.v2";

    /**
     * PST_FEEDING (MOMNA-561, Product decision 24.11.2026), the seven canonical codes registered in the
     * Canonical Field Registry (V33), answered per baby.
     */
    public static final Set<String> FEEDING_CODES = Set.of("nursing_at_breast", "pumped_breast_milk",
        "combination_feeding", "infant_formula", "tube_or_medically_supported", "feeding_plan_changing",
        "prefer_not_to_say");
    /** Breast milk for that baby, whatever the clarification says. */
    public static final Set<String> BREAST_MILK_FEEDING = Set.of("nursing_at_breast", "pumped_breast_milk",
        "combination_feeding");
    /** Breast milk only if the per-baby clarification answers "yes"; "no" or no answer (UNKNOWN) does not count. */
    public static final Set<String> FEEDING_NEEDS_CLARIFICATION = Set.of("tube_or_medically_supported",
        "feeding_plan_changing");
    static final String FEEDING_FIELD = "onboarding.postpartum.feeding";
    static final String BREAST_MILK_NOW_FIELD = "onboarding.postpartum.feeding_breast_milk_now";
    public static final int ACTIVATION_PERCENT = 60;
    public static final int MAX_STATE_KEYS = 3;
    public static final int MAX_FACT_KEYS = 3;
    static final BigDecimal ACTIVATION = new BigDecimal("0.600000");
    static final Set<String> MEDICAL_SEVERITIES = Set.of("MEDIUM", "HIGH", "CRITICAL");
    private static final int SCALE = 6;
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(SCALE);

    private MyDayOverlayAdapter() {}

    /** Which cap a key counts against. */
    public enum Group { STATE, FACT, MEDICAL, NONE }

    /** Source types of the MOMNA-1324 table. */
    public enum SourceType { CHECKIN_ITEM, ONBOARDING_FIELD, CALENDAR_FACT, SAFETY_PROJECTION, NOT_MAPPABLE }

    public record Entry(String key, String lifeMode, SourceType sourceType, Group group, Rule rule,
                        String owner, String meaning) {}

    public record Input(
        String lifeMode,
        LocalDate targetDate,
        List<Observation> observations,
        List<SafetyProjection> safety,
        Map<String, OnboardingFact> onboarding,
        MyDayCalendarFacts calendar
    ) {
        public Input {
            observations = List.copyOf(observations);
            safety = List.copyOf(safety);
            onboarding = Map.copyOf(onboarding);
        }
    }

    public record Activation(String key, Group group, BigDecimal strength, boolean passed) {}

    public record Result(List<String> overlayKeys, List<Activation> activations) {
        public Result {
            overlayKeys = List.copyOf(overlayKeys);
            activations = List.copyOf(activations);
        }
    }

    public static Result compute(Input input) {
        var candidates = new ArrayList<Activation>();
        var entries = catalog(input.lifeMode());
        for (var entry : entries) {
            var strength = entry.rule().evaluate(input);
            if (strength.isPresent()) candidates.add(new Activation(entry.key(), entry.group(), strength.get(), false));
        }
        var order = new HashMap<String, Integer>();
        for (int i = 0; i < entries.size(); i++) order.put(entries.get(i).key(), i);
        var passed = new TreeSet<String>();
        candidates.stream().filter(a -> a.group() == Group.MEDICAL).forEach(a -> passed.add(a.key()));
        candidates.stream().filter(a -> a.group() == Group.STATE)
            .sorted(Comparator.comparing(Activation::strength).reversed()
                .thenComparing(a -> order.get(a.key())))
            .limit(MAX_STATE_KEYS).forEach(a -> passed.add(a.key()));
        candidates.stream().filter(a -> a.group() == Group.FACT)
            .sorted(Comparator.comparing(a -> order.get(a.key())))
            .limit(MAX_FACT_KEYS).forEach(a -> passed.add(a.key()));
        var activations = candidates.stream()
            .map(a -> new Activation(a.key(), a.group(), a.strength(), passed.contains(a.key())))
            .sorted(Comparator.comparing(Activation::key))
            .toList();
        return new Result(List.copyOf(passed), activations);
    }

    /** Candidates before the daily caps: what each key's own rule says. */
    public static SortedSet<String> candidates(Input input) {
        var out = new TreeSet<String>();
        for (var entry : catalog(input.lifeMode())) {
            if (entry.rule().evaluate(input).isPresent()) out.add(entry.key());
        }
        return out;
    }

    // ---------------------------------------------------------------- rules

    public sealed interface Rule {
        Optional<BigDecimal> evaluate(Input input);
    }

    /** The weighted share of "noticeable" over the phases that answered one of these items. */
    public record Checkin(List<String> items) implements Rule {
        public Checkin(String... items) { this(List.of(items)); }

        @Override
        public Optional<BigDecimal> evaluate(Input input) {
            var strength = strength(input.observations());
            return strength.compareTo(ACTIVATION) >= 0 ? Optional.of(strength) : Optional.empty();
        }

        BigDecimal strength(List<Observation> observations) {
            var byPhase = new TreeMap<String, BigDecimal[]>();
            for (var o : observations) {
                if (!items.contains(o.itemCode()) || o.phaseShare().signum() <= 0) continue;
                var level = BigDecimal.valueOf(Math.min(o.statePoints(), 2)).divide(BigDecimal.valueOf(2), SCALE,
                    RoundingMode.HALF_UP);
                var current = byPhase.get(o.phase());
                if (current == null || level.compareTo(current[0]) > 0) byPhase.put(o.phase(), new BigDecimal[] {
                    level, o.phaseShare()});
            }
            var weight = BigDecimal.ZERO;
            var said = BigDecimal.ZERO;
            for (var phase : byPhase.values()) {
                said = said.add(phase[0].multiply(phase[1]));
                weight = weight.add(phase[1]);
            }
            if (weight.signum() == 0) return BigDecimal.ZERO.setScale(SCALE);
            return said.divide(weight, SCALE, RoundingMode.HALF_UP);
        }
    }

    /** A fact of today stated by the morning Check-in: the item was answered with at least this state. */
    public record MorningAnswered(String item, int minState) implements Rule {
        @Override
        public Optional<BigDecimal> evaluate(Input input) {
            return input.observations().stream()
                .anyMatch(o -> "MORNING".equals(o.phase()) && item.equals(o.itemCode()) && o.statePoints() >= minState)
                ? Optional.of(ONE) : Optional.empty();
        }
    }

    public record Onboarding(String field, Set<String> anyOf) implements Rule {
        public Onboarding {
            anyOf = Collections.unmodifiableSortedSet(new TreeSet<>(anyOf));
        }

        public Onboarding(String field, String... anyOf) { this(field, Set.of(anyOf)); }

        @Override
        public Optional<BigDecimal> evaluate(Input input) {
            var fact = input.onboarding().get(field);
            if (fact == null) return Optional.empty();
            return fact.options().stream().anyMatch(anyOf::contains) ? Optional.of(ONE) : Optional.empty();
        }
    }

    /** A dated onboarding answer that falls within [from, to] days of the target day. */
    public record OnboardingDateWithin(String field, int fromDays, int toDays) implements Rule {
        @Override
        public Optional<BigDecimal> evaluate(Input input) {
            var date = MyDayCalendarFacts.date(input.onboarding().get(field));
            if (date.isEmpty()) return Optional.empty();
            var days = ChronoUnit.DAYS.between(input.targetDate(), date.get());
            return days >= fromDays && days <= toDays ? Optional.of(ONE) : Optional.empty();
        }
    }

    public enum CalendarFact { MENSTRUAL, FERTILE_WINDOW, LUTEAL, PREGNANCY_WEEK_28_PLUS, POSTPARTUM_WEEKS_1_4,
        POSTPARTUM_AFTER_WEEK_6, WEEKDAY }

    public record Calendar(CalendarFact fact) implements Rule {
        @Override
        public Optional<BigDecimal> evaluate(Input input) {
            var c = input.calendar();
            boolean holds = switch (fact) {
                case MENSTRUAL -> "menstrual".equals(c.cycleState());
                case FERTILE_WINDOW -> "fertile_window".equals(c.cycleState());
                case LUTEAL -> "luteal".equals(c.cycleState());
                case PREGNANCY_WEEK_28_PLUS -> c.weekNumber() != null && c.weekNumber() >= 28;
                case POSTPARTUM_WEEKS_1_4 -> c.weekNumber() != null && c.weekNumber() <= 4;
                case POSTPARTUM_AFTER_WEEK_6 -> c.weekNumber() != null && c.weekNumber() > 6;
                case WEEKDAY -> input.targetDate().getDayOfWeek() != DayOfWeek.SATURDAY
                    && input.targetDate().getDayOfWeek() != DayOfWeek.SUNDAY;
            };
            return holds ? Optional.of(ONE) : Optional.empty();
        }
    }

    /** The authoritative SafetyDecision of a source session for one of the period's own routes. */
    public record Safety(String routePrefix) implements Rule {
        @Override
        public Optional<BigDecimal> evaluate(Input input) {
            return input.safety().stream().anyMatch(s -> s.routeCode() != null && s.routeCode().startsWith(routePrefix)
                && s.severity() != null && MEDICAL_SEVERITIES.contains(s.severity()))
                ? Optional.of(ONE) : Optional.empty();
        }
    }

    /** Every part holds; the strength is the weakest part. */
    public record All(List<Rule> rules) implements Rule {
        public All(Rule... rules) { this(List.of(rules)); }

        @Override
        public Optional<BigDecimal> evaluate(Input input) {
            BigDecimal min = null;
            for (var rule : rules) {
                var value = rule.evaluate(input);
                if (value.isEmpty()) return Optional.empty();
                min = min == null || value.get().compareTo(min) < 0 ? value.get() : min;
            }
            return Optional.ofNullable(min);
        }
    }

    /** Any part holds; the strength is the strongest part. */
    public record Any(List<Rule> rules) implements Rule {
        public Any(Rule... rules) { this(List.of(rules)); }

        @Override
        public Optional<BigDecimal> evaluate(Input input) {
            return rules.stream().map(r -> r.evaluate(input)).flatMap(Optional::stream).max(Comparator.naturalOrder());
        }
    }

    /**
     * {@code post_breastfeeding}, per baby: on when at least one baby is fed breast milk. For tube/medical
     * feeding or a changing plan the clarification of the same baby decides ("yes" on, "no" off, none
     * UNKNOWN = off). A single clarification answer without child references applies to every such baby.
     */
    public record BreastMilkFeeding() implements Rule {
        @Override
        public Optional<BigDecimal> evaluate(Input input) {
            var feeding = input.onboarding().get(FEEDING_FIELD);
            if (feeding == null) return Optional.empty();
            var clarification = input.onboarding().get(BREAST_MILK_NOW_FIELD);
            var answers = clarification == null ? Map.<String, String>of() : clarification.perChild();
            for (var child : feeding.perChild().entrySet()) {
                var code = child.getValue();
                if (BREAST_MILK_FEEDING.contains(code)) return Optional.of(ONE);
                if (FEEDING_NEEDS_CLARIFICATION.contains(code)) {
                    var now = answers.containsKey(child.getKey()) ? answers.get(child.getKey())
                        : answers.size() == 1 && answers.containsKey("*") ? answers.get("*") : null;
                    if ("yes".equals(now)) return Optional.of(ONE);
                }
            }
            return Optional.empty();
        }
    }

    public record NotMappable(String reason) implements Rule {
        @Override
        public Optional<BigDecimal> evaluate(Input input) { return Optional.empty(); }
    }

    // ---------------------------------------------------------------- the table (order = priority)

    private static final String PRODUCT = "Product (vkdevpro), 23.11.2026";
    private static final String SAFETY = "Safety Platform (MOMNA-1145)";

    private static Entry state(String key, String mode, Rule rule, String meaning) {
        return new Entry(key, mode, SourceType.CHECKIN_ITEM, Group.STATE, rule, PRODUCT, meaning);
    }

    private static Entry fact(String key, String mode, SourceType type, Rule rule, String meaning) {
        return new Entry(key, mode, type, Group.FACT, rule, PRODUCT, meaning);
    }

    private static Entry medical(String key, String mode, String routePrefix, String meaning) {
        return new Entry(key, mode, SourceType.SAFETY_PROJECTION, Group.MEDICAL, new Safety(routePrefix), SAFETY, meaning);
    }

    private static Entry notMappable(String key, String mode, String reason) {
        return new Entry(key, mode, SourceType.NOT_MAPPABLE, Group.NONE, new NotMappable(reason), PRODUCT, reason);
    }

    private static final String C = "onboarding.";
    private static final SourceType OB = SourceType.ONBOARDING_FIELD;
    private static final SourceType CAL = SourceType.CALENDAR_FACT;

    public static final List<Entry> CATALOG = List.of(
        // teen_menarche (7)
        medical("teen_red_flag", "teen_menarche", "MENARCHE_", "SafetyDecision MEDIUM+ on a menarche route"),
        state("teen_pain_high", "teen_menarche", new Checkin("M2", "M11"), "pain M2 / body discomfort M11"),
        state("teen_mood_sensitive", "teen_menarche", new Checkin("M7", "M12"), "mood M7 / anxiety M12"),
        fact("teen_bleeding_now", "teen_menarche", CAL, new Calendar(CalendarFact.MENSTRUAL),
            "cycle days 1-5 from the onboarding last period"),
        fact("teen_school_day", "teen_menarche", SourceType.CHECKIN_ITEM, new Any(
            new MorningAnswered("M6", 0),
            new All(new Onboarding(C + "menarche.day_context", "school"), new Calendar(CalendarFact.WEEKDAY))),
            "morning M6 answered, or school as day context on a weekday"),
        fact("teen_no_trusted_adult", "teen_menarche", OB,
            new Onboarding(C + "menarche.trusted_adult", "no_one", "dont_want_to"), "no trusted adult"),
        fact("teen_privacy_high", "teen_menarche", OB, new Any(
            new Onboarding(C + "menarche.notifications", "neutral_wording", "no_notifications"),
            new Onboarding(C + "menarche.toilet_access", "sometimes_difficult", "almost_impossible"),
            new Checkin("M8")), "neutral notifications, hard toilet access, or privacy M8"),
        // adult_cycle (7)
        medical("adult_red_flag", "adult_cycle", "CYCLE_", "SafetyDecision MEDIUM+ on a cycle route"),
        state("adult_pain_high", "adult_cycle", new Checkin("C3", "C13"), "pain C3 / cycle sensations C13"),
        state("adult_heavy_flow", "adult_cycle", new Checkin("C4"), "bleeding heavier / disruptive C4"),
        state("adult_low_energy", "adult_cycle", new Checkin("C2", "C12"), "energy C2 / sleep debt C12"),
        state("adult_work_stress", "adult_cycle", new Checkin("C8", "C11"), "workload C8 / workload fatigue C11"),
        state("adult_pms", "adult_cycle", new All(new Calendar(CalendarFact.LUTEAL), new Checkin("C5", "C7", "C9")),
            "luteal cycle days and bloating C5 / mood C7 / appetite C9"),
        notMappable("adult_relationship_tension", "adult_cycle",
            "weighted-v3 has no relationship item; onboarding cycle.partner is a status, not tension"),
        // planning (6)
        state("planning_anxiety_high", "planning", new Checkin("T1", "T8"), "emotional state T1 / anxiety T8"),
        fact("planning_fertile_window", "planning", CAL, new Calendar(CalendarFact.FERTILE_WINDOW),
            "Standard Days Method days 8-19 of a regular cycle"),
        fact("planning_health_check", "planning", SourceType.CHECKIN_ITEM, new Any(
            new MorningAnswered("T4", 1), new OnboardingDateWithin(C + "planning.treatment_expected_date", 0, 7)),
            "a test or appointment today (T4), or treatment within 7 days"),
        fact("planning_long_wait", "planning", OB, new Onboarding(C + "planning.duration", "6_12m", "more_than_year"),
            "trying 6+ months"),
        fact("planning_cycle_irregular", "planning", OB,
            new Onboarding(C + "planning.regularity", "timing_changes", "often_irregular"), "irregular cycle"),
        fact("planning_partner_stress", "planning", OB, new Any(
            new Onboarding(C + "planning.partner_involvement", "tension"),
            new Onboarding(C + "planning.pressure", "relationship_tension")),
            "tension with the partner (pressured_unsafe is a safety matter, not this key)"),
        // pregnancy (9)
        medical("preg_high_risk", "pregnancy", "PREGNANCY_", "SafetyDecision MEDIUM+ on a pregnancy route"),
        state("preg_nausea", "pregnancy", new Checkin("P3"), "nausea P3"),
        state("preg_fatigue", "pregnancy", new Checkin("P2", "P15"), "energy P2 / tiredness P15"),
        state("preg_anxiety", "pregnancy", new Checkin("P18"), "emotional load P18"),
        fact("preg_third_trimester", "pregnancy", CAL, new Calendar(CalendarFact.PREGNANCY_WEEK_28_PLUS),
            "week 28+"),
        fact("preg_first_pregnancy", "pregnancy", OB,
            new Onboarding(C + "pregnancy.preg_history", "first_pregnancy"), "first pregnancy"),
        fact("preg_partner_present", "pregnancy", OB, new Onboarding(C + "pregnancy.partner", "yes_live_together"),
            "partner lives together"),
        fact("preg_partner_absent", "pregnancy", OB,
            new Onboarding(C + "pregnancy.partner", "yes_no_live_together", "no_partner"), "partner not near"),
        fact("preg_work_active", "pregnancy", OB, new Onboarding(C + "pregnancy.occupation", "work"), "works"),
        // postpartum (9)
        notMappable("post_ppd_screen", "postpartum",
            "MOMNA-1146: no approved mental-health route; never from a score"),
        fact("post_birth_week_1_4", "postpartum", CAL, new Calendar(CalendarFact.POSTPARTUM_WEEKS_1_4),
            "weeks 1-4 after the birth date"),
        fact("post_c_section", "postpartum", OB, new Onboarding(C + "postpartum.delivery",
            "planned_c_section", "emergency_c_section", "multiples_all_c_section", "multiples_mixed"),
            "caesarean birth"),
        fact("post_low_milk", "postpartum", OB, new Onboarding(C + "postpartum.feeding_issues", "milk_supply_worry"),
            "milk supply worry"),
        fact("post_breastfeeding", "postpartum", OB, new BreastMilkFeeding(),
            "PST_FEEDING (MOMNA-561), per baby: nursing_at_breast, pumped_breast_milk or combination_feeding; "
                + "tube_or_medically_supported / feeding_plan_changing only with breast milk now = yes; "
                + "at least one baby; feeding_issues never"),
        fact("post_no_partner", "postpartum", OB,
            new Onboarding(C + "postpartum.partner", "no_partner", "not_living_together"), "no partner near"),
        fact("post_partner_present", "postpartum", OB, new Onboarding(C + "postpartum.partner", "live_together"),
            "partner lives together"),
        fact("post_returning_work", "postpartum", OB,
            new Onboarding(C + "postpartum.work", "need_return_soon", "from_home", "in_person", "shifts"),
            "back at work or returning soon"),
        fact("post_intimacy_ready", "postpartum", OB, new All(
            new Onboarding(C + "postpartum.intimacy_status", "yes_regularly", "sometimes"),
            new Calendar(CalendarFact.POSTPARTUM_AFTER_WEEK_6)), "intimacy resumed, after week 6"),
        // perimenopause (7)
        state("peri_hot_flashes", "perimenopause", new Checkin("PM2", "PM9"), "hot flashes PM2 / PM9"),
        state("peri_sleep_disrupted", "perimenopause", new Checkin("PM1"), "sleep PM1"),
        state("peri_mood_shift", "perimenopause", new Checkin("PM5", "PM10"), "mood PM5 / tension PM10"),
        state("peri_brain_fog", "perimenopause", new Checkin("PM4", "PM11"), "concentration PM4 / PM11"),
        state("peri_work_challenge", "perimenopause", new All(
            new Onboarding(C + "perimenopause.occupation", "work"), new Checkin("PM6"),
            new Any(new Checkin("PM2", "PM9"), new Checkin("PM4", "PM11"), new Checkin("PM1"))),
            "works, packed day PM6 and a symptom key's items"),
        fact("peri_cycle_irregular", "perimenopause", OB,
            new Onboarding(C + "perimenopause.changes", "earlier", "later", "skip_some"), "cycle timing changes"),
        fact("peri_low_libido", "perimenopause", OB, new Onboarding(C + "perimenopause.intimacy", "desire_decreased"),
            "desire decreased"),
        // menopause (7)
        state("meno_sleep_poor", "menopause", new Checkin("MN1"), "sleep MN1"),
        state("meno_hot_flashes", "menopause", new Checkin("MN3", "MN13"), "hot flashes MN3 / MN13"),
        state("meno_joint_pain", "menopause", new Checkin("MN4"), "joint discomfort MN4"),
        state("meno_mood_low", "menopause", new Checkin("MN7"), "mood MN7"),
        state("meno_vaginal_dryness", "menopause", new Checkin("MN8"), "urogenital discomfort MN8"),
        fact("meno_active_work", "menopause", OB, new Onboarding(C + "menopause.occupation", "work"), "works"),
        notMappable("meno_new_chapter", "menopause",
            "no Check-in item or onboarding field states a new project; needs a Product question")
    );

    public static List<Entry> catalog(String lifeMode) {
        return CATALOG.stream().filter(e -> e.lifeMode().equals(lifeMode)).toList();
    }

    public static Set<String> keys(String lifeMode) {
        var out = new TreeSet<String>();
        catalog(lifeMode).forEach(e -> out.add(e.key()));
        return Collections.unmodifiableSet(out);
    }

    /** Onboarding fields the adapter and the calendar facts may read; nothing else is requested. */
    public static final Set<String> ONBOARDING_FIELDS = onboardingFields();

    private static Set<String> onboardingFields() {
        var out = new TreeSet<String>(List.of(
            "onboarding.menarche.last_period", "onboarding.cycle.lmp", "onboarding.cycle.regularity",
            "onboarding.planning.lmp", "onboarding.perimenopause.lmp", "onboarding.pregnancy.edd",
            "onboarding.pregnancy.lmp", "onboarding.pregnancy.gestational_weeks", "onboarding.postpartum.birth_date",
            FEEDING_FIELD, BREAST_MILK_NOW_FIELD));
        for (var entry : CATALOG) collect(entry.rule(), out);
        return Collections.unmodifiableSet(out);
    }

    private static void collect(Rule rule, Set<String> out) {
        switch (rule) {
            case Onboarding o -> out.add(o.field());
            case OnboardingDateWithin o -> out.add(o.field());
            case All a -> a.rules().forEach(r -> collect(r, out));
            case Any a -> a.rules().forEach(r -> collect(r, out));
            default -> { }
        }
    }
}
