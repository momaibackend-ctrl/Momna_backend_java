package com.momna.modules.flow.application;

import com.momna.modules.lifecycle.domain.LifecyclePeriod;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RouterRoutingTable {
    private RouterRoutingTable() {}

    public enum Confidence {
        LOW, MEDIUM, HIGH
    }

    public record Decision(
        LifecyclePeriod period,
        String substage,
        Confidence confidence,
        Set<String> additionalContexts,
        Integer matchedPriority
    ) {}

    private record Rule(
        int priority,
        String fieldId,
        Set<String> values,
        LifecyclePeriod period,
        String substage,
        Confidence confidence,
        Set<String> additionalContexts
    ) {}

    private static Rule rule(
        int priority,
        String fieldId,
        Set<String> values,
        LifecyclePeriod period,
        String substage,
        Confidence confidence
    ) {
        return new Rule(
            priority, fieldId, values, period, substage,
            confidence, Set.of()
        );
    }

    private static Rule rule(
        int priority,
        String fieldId,
        Set<String> values,
        LifecyclePeriod period,
        String substage,
        Confidence confidence,
        Set<String> additionalContexts
    ) {
        return new Rule(
            priority, fieldId, values, period, substage,
            confidence, additionalContexts
        );
    }

    private static final List<Rule> RULES = List.of(
        rule(10, "onboarding.router.pregnancy_status", Set.of("confirmed_by_doctor"), LifecyclePeriod.PREGNANCY, "CONFIRMED", Confidence.HIGH),
        rule(11, "onboarding.router.pregnancy_status", Set.of("test_positive"), LifecyclePeriod.PREGNANCY, "TEST_POSITIVE", Confidence.HIGH),
        rule(12, "onboarding.router.planning_pregnancy_status", Set.of("confirmed"), LifecyclePeriod.PREGNANCY, "CONFIRMED", Confidence.HIGH),
        rule(13, "onboarding.router.unsure_pregnancy", Set.of("confirmed"), LifecyclePeriod.PREGNANCY, "CONFIRMED", Confidence.HIGH),
        rule(14, "onboarding.router.cycle_change_known_cause", Set.of("pregnancy"), LifecyclePeriod.PREGNANCY, "UNCONFIRMED", Confidence.MEDIUM),
        rule(15, "onboarding.router.amenorrhea_known_cause", Set.of("pregnancy"), LifecyclePeriod.PREGNANCY, "UNCONFIRMED", Confidence.MEDIUM),

        rule(20, "onboarding.router.postpartum_time_range", Set.of("lt_1m"), LifecyclePeriod.POSTPARTUM, "LT_1M", Confidence.HIGH),
        rule(21, "onboarding.router.postpartum_time_range", Set.of("m1_3"), LifecyclePeriod.POSTPARTUM, "M1_3", Confidence.HIGH),
        rule(22, "onboarding.router.postpartum_time_range", Set.of("m4_6"), LifecyclePeriod.POSTPARTUM, "M4_6", Confidence.HIGH),
        rule(23, "onboarding.router.postpartum_time_range", Set.of("m7_9"), LifecyclePeriod.POSTPARTUM, "M7_9", Confidence.HIGH),
        rule(24, "onboarding.router.postpartum_time_range", Set.of("m10_12"), LifecyclePeriod.POSTPARTUM, "M10_12", Confidence.HIGH),
        rule(25, "onboarding.router.postpartum_main_need", Set.of("recovery"), LifecyclePeriod.POSTPARTUM, "EXTENDED_RECOVERY", Confidence.MEDIUM),
        rule(26, "onboarding.router.unsure_recent_birth", Set.of("yes"), LifecyclePeriod.POSTPARTUM, "UNSPECIFIED", Confidence.MEDIUM),
        rule(27, "onboarding.router.cycle_change_known_cause", Set.of("recent_birth_or_breastfeeding"), LifecyclePeriod.POSTPARTUM, "UNSPECIFIED", Confidence.MEDIUM),
        rule(28, "onboarding.router.amenorrhea_known_cause", Set.of("recent_birth_or_breastfeeding"), LifecyclePeriod.POSTPARTUM, "UNSPECIFIED", Confidence.MEDIUM),

        rule(30, "onboarding.router.menarche_status", Set.of("not_started"), LifecyclePeriod.MENARCHE, "PRE_MENARCHE", Confidence.HIGH),
        rule(31, "onboarding.router.menarche_status", Set.of("very_recent"), LifecyclePeriod.MENARCHE, "EARLY_MENARCHE", Confidence.HIGH),
        rule(32, "onboarding.router.menarche_status", Set.of("within_two_years"), LifecyclePeriod.MENARCHE, "CYCLE_FORMATION", Confidence.HIGH),
        rule(33, "onboarding.router.menarche_status", Set.of("unsure"), LifecyclePeriod.MENARCHE, "UNCERTAIN_ONSET", Confidence.MEDIUM),

        rule(40, "onboarding.router.planning_pregnancy_status", Set.of("no"), LifecyclePeriod.PLANNING, "PREPARING", Confidence.HIGH),
        rule(41, "onboarding.router.planning_pregnancy_status", Set.of("maybe"), LifecyclePeriod.PLANNING, "PREPARING", Confidence.MEDIUM, Set.of("POSSIBLE_PREGNANCY")),
        rule(42, "onboarding.router.postpartum_main_need", Set.of("planning"), LifecyclePeriod.PLANNING, "PREPARING", Confidence.MEDIUM),
        rule(43, "onboarding.router.unsure_planning", Set.of("yes"), LifecyclePeriod.PLANNING, "PREPARING", Confidence.HIGH),
        rule(44, "onboarding.router.planning_pregnancy_status", Set.of("prefer_not_to_say"), LifecyclePeriod.PLANNING, "PREPARING", Confidence.MEDIUM),

        rule(49, "onboarding.router.amenorrhea_known_cause", Set.of("ovaries_surgically_removed"), LifecyclePeriod.MENOPAUSE, "CONFIRMED", Confidence.HIGH),
        rule(50, "onboarding.router.amenorrhea_known_cause", Set.of("doctor_confirmed_menopause"), LifecyclePeriod.MENOPAUSE, "CONFIRMED", Confidence.HIGH),
        rule(51, "onboarding.router.amenorrhea_known_cause", Set.of("no_known_cause"), LifecyclePeriod.MENOPAUSE, "UNCONFIRMED", Confidence.MEDIUM),
        rule(52, "onboarding.router.amenorrhea_known_cause", Set.of("do_not_know"), LifecyclePeriod.MENOPAUSE, "UNCONFIRMED", Confidence.LOW),
        rule(53, "onboarding.router.unsure_no_periods", Set.of("yes"), LifecyclePeriod.MENOPAUSE, "UNCONFIRMED", Confidence.MEDIUM),
        rule(54, "onboarding.router.amenorrhea_known_cause", Set.of("prefer_not_to_say"), LifecyclePeriod.MENOPAUSE, "UNCONFIRMED", Confidence.LOW),

        rule(60, "onboarding.router.cycle_change_known_cause", Set.of("doctor_said_perimenopause"), LifecyclePeriod.PERIMENOPAUSE, "CONFIRMED_CONTEXT", Confidence.HIGH),
        rule(61, "onboarding.router.cycle_change_known_cause", Set.of("no_known_cause"), LifecyclePeriod.PERIMENOPAUSE, "POSSIBLE", Confidence.MEDIUM),
        rule(62, "onboarding.router.cycle_change_known_cause", Set.of("do_not_know"), LifecyclePeriod.PERIMENOPAUSE, "POSSIBLE", Confidence.LOW),
        rule(63, "onboarding.router.unsure_cycle_changing", Set.of("yes"), LifecyclePeriod.PERIMENOPAUSE, "POSSIBLE", Confidence.MEDIUM),

        rule(70, "onboarding.router.pregnancy_status", Set.of("only_possible"), LifecyclePeriod.CYCLE, "STANDARD_CYCLE", Confidence.MEDIUM, Set.of("POSSIBLE_PREGNANCY")),
        rule(71, "onboarding.router.unsure_pregnancy", Set.of("possible"), LifecyclePeriod.CYCLE, "STANDARD_CYCLE", Confidence.MEDIUM, Set.of("POSSIBLE_PREGNANCY")),
        rule(72, "onboarding.router.cycle_change_known_cause", Set.of("hormonal", "surgery_or_treatment", "stress_weight_illness"), LifecyclePeriod.CYCLE, "OBSERVATION_CONTEXT", Confidence.MEDIUM),
        rule(73, "onboarding.router.amenorrhea_known_cause", Set.of("hormonal", "hysterectomy_or_other_treatment"), LifecyclePeriod.CYCLE, "OBSERVATION_CONTEXT", Confidence.MEDIUM),
        rule(74, "onboarding.router.postpartum_main_need", Set.of("cycle"), LifecyclePeriod.CYCLE, "STANDARD_CYCLE", Confidence.HIGH),
        rule(75, "onboarding.router.current_situation", Set.of("cycle_tracking"), LifecyclePeriod.CYCLE, "STANDARD_CYCLE", Confidence.HIGH)
    );

    public static Decision route(Map<String, String> answers) {
        for (var rule : RULES) {
            if (rule.values().contains(answers.get(rule.fieldId()))) {
                return new Decision(
                    rule.period(),
                    rule.substage(),
                    rule.confidence(),
                    rule.additionalContexts(),
                    rule.priority()
                );
            }
        }
        return new Decision(
            LifecyclePeriod.CYCLE,
            "STANDARD_CYCLE",
            Confidence.LOW,
            Set.of(),
            null
        );
    }
}
