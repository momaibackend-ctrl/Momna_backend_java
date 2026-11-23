package com.momna.modules.myday.application.template;

import com.momna.modules.myday.application.template.MyDayFacts.OnboardingFact;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Calendar inputs of the My Day prompts (MOMNA-1325), derived from dated onboarding answers only.
 * An unknown fact stays {@code uncertain} / {@code null}; nothing is guessed beyond the rules below.
 *
 * <ul>
 *   <li>{@code cycle_state}: cycle day = days since the onboarding LMP (day 1 = LMP). Days 1-5 are
 *       {@code menstrual}. Only for a cycle reported as regular (adult cycle, planning) the Standard Days
 *       Method windows apply (WHO / Georgetown IRH, cycles of 26-32 days): days 6-7 {@code follicular},
 *       8-19 {@code fertile_window}, 20-32 {@code luteal}. Anything else is {@code uncertain};
 *       {@code ovulation_peak} needs ovulation data and is not derived. Without a cycle:
 *       {@code not_applicable}.</li>
 *   <li>Pregnancy week: completed weeks of gestation (ACOG), from the due date (280 days), else the
 *       LMP, else the reported week advanced by the days since it was given; valid up to 44 weeks,
 *       reported as 1-42. Trimesters (ACOG): 1 up to 13 6/7 weeks, 2 from 14 0/7, 3 from 28 0/7.</li>
 *   <li>Postpartum week: week 1 is days 0-6 after the birth date, valid up to week 52.</li>
 *   <li>{@code day_number}: the day in the current life mode, day 1 = the day it started.</li>
 *   <li>{@code phase_code} ({@value #PHASE_CODE_VERSION}): {@code teen_|adult_|planning_|peri_} + cycle
 *       state, {@code preg_trimester<n>_w<week>}, {@code post_w<week>}, {@code meno_general}; an unknown
 *       week gives {@code preg_unknown} / {@code post_unknown}.</li>
 * </ul>
 */
public record MyDayCalendarFacts(
    int dayNumber,
    String cycleState,
    String phaseCode,
    Integer weekNumber,
    String trimester
) {
    public static final String VERSION = "myday.calendar-facts.v1";
    public static final String PHASE_CODE_VERSION = "myday.phase-code.v1";

    private static final Map<String, String> LMP_FIELD = Map.of(
        "teen_menarche", "onboarding.menarche.last_period",
        "adult_cycle", "onboarding.cycle.lmp",
        "planning", "onboarding.planning.lmp",
        "perimenopause", "onboarding.perimenopause.lmp"
    );
    private static final Map<String, String[]> REGULAR = Map.of(
        "adult_cycle", new String[] {"onboarding.cycle.regularity", "around_expected"},
        "planning", new String[] {"onboarding.planning.regularity", "fairly_predictable"}
    );

    public static MyDayCalendarFacts derive(
        String lifeMode,
        Map<String, OnboardingFact> onboarding,
        LocalDate target,
        LocalDate lifeModeStart,
        ZoneId zone
    ) {
        var dayNumber = lifeModeStart == null || lifeModeStart.isAfter(target)
            ? 1
            : (int) Math.min(Integer.MAX_VALUE, ChronoUnit.DAYS.between(lifeModeStart, target) + 1);
        var cycleState = cycleState(lifeMode, onboarding, target);
        Integer week = switch (lifeMode) {
            case "pregnancy" -> pregnancyWeek(onboarding, target, zone);
            case "postpartum" -> postpartumWeek(onboarding, target);
            default -> null;
        };
        var trimester = "pregnancy".equals(lifeMode) ? trimester(week) : null;
        return new MyDayCalendarFacts(dayNumber, cycleState, phaseCode(lifeMode, cycleState, week, trimester),
            week, trimester);
    }

    static String cycleState(String lifeMode, Map<String, OnboardingFact> onboarding, LocalDate target) {
        var field = LMP_FIELD.get(lifeMode);
        if (field == null) return "not_applicable";
        var lmp = date(onboarding.get(field));
        if (lmp.isEmpty()) return "uncertain";
        var day = ChronoUnit.DAYS.between(lmp.get(), target) + 1;
        if (day < 1) return "uncertain";
        if (day <= 5) return "menstrual";
        var regular = REGULAR.get(lifeMode);
        boolean isRegular = regular != null && onboarding.containsKey(regular[0])
            && onboarding.get(regular[0]).options().contains(regular[1]);
        if (!isRegular) return "uncertain";
        if (day <= 7) return "follicular";
        if (day <= 19) return "fertile_window";
        if (day <= 32) return "luteal";
        return "uncertain";
    }

    static Integer pregnancyWeek(Map<String, OnboardingFact> onboarding, LocalDate target, ZoneId zone) {
        Long gestationDays = null;
        var edd = date(onboarding.get("onboarding.pregnancy.edd"));
        var lmp = date(onboarding.get("onboarding.pregnancy.lmp"));
        var reported = onboarding.get("onboarding.pregnancy.gestational_weeks");
        if (edd.isPresent()) {
            gestationDays = 280 - ChronoUnit.DAYS.between(target, edd.get());
        } else if (lmp.isPresent()) {
            gestationDays = ChronoUnit.DAYS.between(lmp.get(), target);
        } else if (reported != null && reported.answeredAt() != null) {
            var weeks = number(reported);
            if (weeks.isPresent()) {
                var answeredOn = reported.answeredAt().atZone(zone).toLocalDate();
                gestationDays = weeks.get() * 7L + ChronoUnit.DAYS.between(answeredOn, target);
            }
        }
        if (gestationDays == null || gestationDays < 0 || gestationDays > 44 * 7) return null;
        return (int) Math.max(1, Math.min(42, gestationDays / 7));
    }

    static String trimester(Integer week) {
        if (week == null) return null;
        if (week < 14) return "trimester_1";
        if (week < 28) return "trimester_2";
        return "trimester_3";
    }

    static Integer postpartumWeek(Map<String, OnboardingFact> onboarding, LocalDate target) {
        var birth = date(onboarding.get("onboarding.postpartum.birth_date"));
        if (birth.isEmpty()) return null;
        var days = ChronoUnit.DAYS.between(birth.get(), target);
        if (days < 0 || days >= 52 * 7) return null;
        return (int) (days / 7) + 1;
    }

    static String phaseCode(String lifeMode, String cycleState, Integer week, String trimester) {
        return switch (lifeMode) {
            case "teen_menarche" -> "teen_" + cycleState;
            case "adult_cycle" -> "adult_" + cycleState;
            case "planning" -> "planning_" + cycleState;
            case "perimenopause" -> "peri_" + cycleState;
            case "pregnancy" -> week == null ? "preg_unknown"
                : "preg_" + trimester.replace("_", "") + "_w" + week;
            case "postpartum" -> week == null ? "post_unknown" : "post_w" + week;
            case "menopause" -> "meno_general";
            default -> throw new IllegalArgumentException("Unknown life mode");
        };
    }

    static Optional<LocalDate> date(OnboardingFact fact) {
        if (fact == null) return Optional.empty();
        try {
            return fact.scalar().map(value -> LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    static Optional<Integer> number(OnboardingFact fact) {
        try {
            return fact.scalar().map(value -> new java.math.BigDecimal(value).intValueExact());
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }
}
