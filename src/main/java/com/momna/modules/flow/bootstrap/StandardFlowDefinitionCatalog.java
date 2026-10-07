package com.momna.modules.flow.bootstrap;

import com.momna.modules.flow.domain.FlowType;
import java.util.*;

public final class StandardFlowDefinitionCatalog {
    private StandardFlowDefinitionCatalog() {}

    public record Spec(
        String key,
        int version,
        FlowType type,
        String period,
        Map<String, Object> definitionJson
    ) {}

    public static List<Spec> all() {
        return List.of(router(), cycle(), pregnancy(), perimenopause(), menopause());
    }

    private static Spec router() {
        return spec("lifecycle-router", 1, FlowType.LIFECYCLE_ROUTER, null, "LIFECYCLE_ROUTE", List.of(
            step("display_name", "profile.display_name", false, true),
            step("country", "profile.country_region", false, true),
            step("birth_date", "profile.birth_date", false, true),
            step("menarche_status", "onboarding.router.menarche_status", true, true),
            step("current_situation", "onboarding.router.current_situation", true, true,
                in("onboarding.router.menarche_status", "more_than_two_years", "prefer_not_to_say")),
            step("pregnancy_status", "onboarding.router.pregnancy_status", false, true,
                in("onboarding.router.current_situation", "pregnant", "possible_pregnancy")),
            step("postpartum_time_range", "onboarding.router.postpartum_time_range", false, true,
                in("onboarding.router.current_situation", "recent_birth")),
            step("postpartum_main_need", "onboarding.router.postpartum_main_need", false, true,
                in("onboarding.router.postpartum_time_range", "more_than_year")),
            step("planning_pregnancy_status", "onboarding.router.planning_pregnancy_status", false, true,
                in("onboarding.router.current_situation", "planning")),
            step("cycle_change_known_cause", "onboarding.router.cycle_change_known_cause", false, true,
                in("onboarding.router.current_situation", "cycle_changing")),
            step("amenorrhea_known_cause", "onboarding.router.amenorrhea_known_cause", false, true,
                in("onboarding.router.current_situation", "no_periods_12m")),
            step("unsure_pregnancy", "onboarding.router.unsure_pregnancy", false, true,
                in("onboarding.router.current_situation", "unsure")),
            step("unsure_recent_birth", "onboarding.router.unsure_recent_birth", false, true,
                in("onboarding.router.unsure_pregnancy", "no")),
            step("unsure_planning", "onboarding.router.unsure_planning", false, true,
                in("onboarding.router.unsure_recent_birth", "no")),
            step("unsure_cycle_changing", "onboarding.router.unsure_cycle_changing", false, true,
                in("onboarding.router.unsure_planning", "no")),
            step("unsure_no_periods", "onboarding.router.unsure_no_periods", false, true,
                in("onboarding.router.unsure_cycle_changing", "no"))
        ));
    }

    private static Spec cycle() {
        return spec("period-onboarding-cycle", 1, FlowType.PERIOD_ONBOARDING, "CYCLE", "PERIOD_ONBOARDING", List.of(
            step("goals", "onboarding.cycle.goals", true, true),
            step("lmp", "onboarding.cycle.lmp", true, true),
            step("regularity", "onboarding.cycle.regularity", true, true),
            step("flow", "onboarding.cycle.flow", true, true),
            step("symptoms", "onboarding.cycle.symptoms", true, true),
            step("impact", "onboarding.cycle.impact", true, true),
            step("diagnoses", "onboarding.cycle.diagnoses", true, true),
            step("meds", "onboarding.cycle.meds", true, true),
            step("preg_history", "onboarding.cycle.preg_history", true, true),
            step("birth_history", "onboarding.cycle.birth_history", true, true,
                selectedAny("onboarding.cycle.preg_history", "ended_in_birth")),
            step("family", "onboarding.cycle.family", true, true),
            step("health_behavior", "onboarding.cycle.health_behavior", true, true),
            step("occupation", "onboarding.cycle.occupation", true, true),
            step("occupation_detail", "onboarding.cycle.occupation_detail", false, true,
                in("onboarding.cycle.occupation", "enter_job")),
            step("work_nature", "onboarding.cycle.work_nature", true, true),
            step("sleep", "onboarding.cycle.sleep", true, true),
            step("activity", "onboarding.cycle.activity", true, true),
            step("mental", "onboarding.cycle.mental", true, true),
            step("health_barrier", "onboarding.cycle.health_barrier", true, true),
            step("rest", "onboarding.cycle.rest", true, true),
            step("partner", "onboarding.cycle.partner", false, true),
            step("partner_support", "onboarding.cycle.partner_support", false, true,
                in("onboarding.cycle.partner", "one_steady", "relationship_no_live", "casual_multiple")),
            step("sex_active", "onboarding.cycle.sex_active", false, true),
            step("sex_freq", "onboarding.cycle.sex_freq", false, true,
                in("onboarding.cycle.sex_active", "yes_regularly", "sometimes")),
            step("sex_comfort", "onboarding.cycle.sex_comfort", false, true,
                in("onboarding.cycle.sex_active", "yes_regularly", "sometimes")),
            step("protection", "onboarding.cycle.protection", false, true,
                in("onboarding.cycle.sex_active", "yes_regularly", "sometimes")),
            step("behavior", "onboarding.cycle.behavior", true, true),
            step("tone", "onboarding.cycle.tone", true, true)
        ));
    }

    private static Spec pregnancy() {
        return spec("period-onboarding-pregnancy", 1, FlowType.PERIOD_ONBOARDING, "PREGNANCY", "PERIOD_ONBOARDING", List.of(
            step("dates_method", "onboarding.pregnancy.dates_method", true, true),
            step("gestational_weeks", "onboarding.pregnancy.gestational_weeks", true, true,
                in("onboarding.pregnancy.dates_method", "gestational_age")),
            step("edd", "onboarding.pregnancy.edd", true, true,
                in("onboarding.pregnancy.dates_method", "edd")),
            step("lmp", "onboarding.pregnancy.lmp", true, true,
                in("onboarding.pregnancy.dates_method", "unknown")),
            step("multiples", "onboarding.pregnancy.multiples", true, true),
            step("goals", "onboarding.pregnancy.goals", true, true),
            step("care", "onboarding.pregnancy.care", true, true),
            step("special_care", "onboarding.pregnancy.special_care", true, true),
            step("symptoms", "onboarding.pregnancy.symptoms", true, true),
            step("preg_history", "onboarding.pregnancy.preg_history", true, true),
            step("prev_details", "onboarding.pregnancy.prev_details", true, true,
                not(selectedAny("onboarding.pregnancy.preg_history", "first_pregnancy"))),
            step("current_context", "onboarding.pregnancy.current_context", true, true),
            step("meds", "onboarding.pregnancy.meds", true, true),
            step("family_context", "onboarding.pregnancy.family_context", true, true),
            step("family_details", "onboarding.pregnancy.family_details", true, true,
                in("onboarding.pregnancy.family_context", "testing_complete", "testing_scheduled", "next_step_undecided")),
            step("occupation", "onboarding.pregnancy.occupation", true, true),
            step("occupation_detail", "onboarding.pregnancy.occupation_detail", false, true,
                in("onboarding.pregnancy.occupation", "enter_job")),
            step("work_nature", "onboarding.pregnancy.work_nature", true, true),
            step("activity", "onboarding.pregnancy.activity", true, true),
            step("sleep", "onboarding.pregnancy.sleep", true, true),
            step("eating", "onboarding.pregnancy.eating", true, true),
            step("mental", "onboarding.pregnancy.mental", true, true),
            step("fears", "onboarding.pregnancy.fears", true, true),
            step("support_network", "onboarding.pregnancy.support_network", true, true),
            step("rest", "onboarding.pregnancy.rest", true, true),
            step("partner", "onboarding.pregnancy.partner", false, true),
            step("partner_support", "onboarding.pregnancy.partner_support", false, true,
                in("onboarding.pregnancy.partner", "yes_live_together", "yes_no_live_together", "casual_multiple")),
            step("sex_active", "onboarding.pregnancy.sex_active", false, true),
            step("sex_freq", "onboarding.pregnancy.sex_freq", false, true,
                in("onboarding.pregnancy.sex_active", "yes_regularly", "sometimes")),
            step("intimacy", "onboarding.pregnancy.intimacy", false, true,
                in("onboarding.pregnancy.sex_active", "yes_regularly", "sometimes")),
            step("behavior", "onboarding.pregnancy.behavior", true, true),
            step("tone", "onboarding.pregnancy.tone", true, true)
        ));
    }

    private static Spec perimenopause() {
        return spec("period-onboarding-perimenopause", 1, FlowType.PERIOD_ONBOARDING, "PERIMENOPAUSE", "PERIOD_ONBOARDING", List.of(
            step("changes", "onboarding.perimenopause.changes", true, true),
            step("lmp", "onboarding.perimenopause.lmp", true, true),
            step("symptoms", "onboarding.perimenopause.symptoms", true, true),
            step("impact", "onboarding.perimenopause.impact", true, true),
            step("daypart", "onboarding.perimenopause.daypart", true, true),
            step("confirmation", "onboarding.perimenopause.confirmation", true, true),
            step("treatment", "onboarding.perimenopause.treatment", false, true),
            step("contraception", "onboarding.perimenopause.contraception", false, true),
            step("surgery", "onboarding.perimenopause.surgery", false, true),
            step("repro_history", "onboarding.perimenopause.repro_history", false, true),
            step("conditions", "onboarding.perimenopause.conditions", false, true),
            step("family", "onboarding.perimenopause.family", false, true),
            step("care", "onboarding.perimenopause.care", true, true),
            step("occupation", "onboarding.perimenopause.occupation", false, true),
            step("occupation_detail", "onboarding.perimenopause.occupation_detail", false, true,
                in("onboarding.perimenopause.occupation", "enter_job")),
            step("work", "onboarding.perimenopause.work", true, true),
            step("sleep", "onboarding.perimenopause.sleep", true, true),
            step("activity", "onboarding.perimenopause.activity", true, true),
            step("lifestyle", "onboarding.perimenopause.lifestyle", false, true),
            step("mental", "onboarding.perimenopause.mental", true, true),
            step("coping", "onboarding.perimenopause.coping", true, true),
            step("partner", "onboarding.perimenopause.partner", false, true),
            step("partner_support", "onboarding.perimenopause.partner_support", false, true,
                in("onboarding.perimenopause.partner", "live_together", "not_living_together", "casual_multiple")),
            step("sex_active", "onboarding.perimenopause.sex_active", false, true),
            step("sex_freq", "onboarding.perimenopause.sex_freq", false, true,
                in("onboarding.perimenopause.sex_active", "yes_regularly", "sometimes")),
            step("intimacy", "onboarding.perimenopause.intimacy", false, true,
                in("onboarding.perimenopause.sex_active", "yes_regularly", "sometimes")),
            step("support", "onboarding.perimenopause.support", false, true),
            step("rest", "onboarding.perimenopause.rest", true, true),
            step("behavior", "onboarding.perimenopause.behavior", true, true),
            step("tone", "onboarding.perimenopause.tone", true, true)
        ));
    }

    private static Spec menopause() {
        return spec("period-onboarding-menopause", 1, FlowType.PERIOD_ONBOARDING, "MENOPAUSE", "PERIOD_ONBOARDING", List.of(
            step("timing", "onboarding.menopause.timing", true, true),
            step("cause", "onboarding.menopause.cause", false, true),
            step("symptoms", "onboarding.menopause.symptoms", true, true),
            step("impact", "onboarding.menopause.impact", true, true),
            step("urogenital", "onboarding.menopause.urogenital", false, true),
            step("mental", "onboarding.menopause.mental", true, true),
            step("sleep", "onboarding.menopause.sleep", true, true),
            step("treatment", "onboarding.menopause.treatment", false, true),
            step("meds", "onboarding.menopause.meds", false, true),
            step("conditions", "onboarding.menopause.conditions", false, true),
            step("family", "onboarding.menopause.family", false, true),
            step("bone", "onboarding.menopause.bone", false, true),
            step("cardio", "onboarding.menopause.cardio", false, true),
            step("repro_history", "onboarding.menopause.repro_history", false, true),
            step("care", "onboarding.menopause.care", true, true),
            step("occupation", "onboarding.menopause.occupation", false, true),
            step("occupation_detail", "onboarding.menopause.occupation_detail", false, true,
                in("onboarding.menopause.occupation", "enter_job")),
            step("work", "onboarding.menopause.work", true, true),
            step("activity", "onboarding.menopause.activity", true, true),
            step("lifestyle", "onboarding.menopause.lifestyle", false, true),
            step("health_barrier", "onboarding.menopause.health_barrier", true, true),
            step("partner", "onboarding.menopause.partner", false, true),
            step("partner_support", "onboarding.menopause.partner_support", false, true,
                in("onboarding.menopause.partner", "live_together", "not_living_together", "casual_multiple")),
            step("sex_active", "onboarding.menopause.sex_active", false, true),
            step("sex_freq", "onboarding.menopause.sex_freq", false, true,
                in("onboarding.menopause.sex_active", "yes_regularly", "sometimes")),
            step("intimacy", "onboarding.menopause.intimacy", false, true,
                in("onboarding.menopause.sex_active", "yes_regularly", "sometimes")),
            step("support", "onboarding.menopause.support", false, true),
            step("rest", "onboarding.menopause.rest", true, true),
            step("goals", "onboarding.menopause.goals", true, true),
            step("behavior", "onboarding.menopause.behavior", true, true),
            step("tone", "onboarding.menopause.tone", true, true)
        ));
    }

    private static Spec spec(
        String key, int version, FlowType type, String period, String resultKind, List<Map<String, Object>> steps
    ) {
        var json = new LinkedHashMap<String, Object>();
        json.put("resultKind", resultKind);
        json.put("steps", steps);
        return new Spec(key, version, type, period, Map.copyOf(json));
    }

    private static Map<String, Object> step(String id, String field, boolean required, boolean skippable) {
        return step(id, field, required, skippable, null);
    }

    private static Map<String, Object> step(
        String id, String field, boolean required, boolean skippable, Map<String, Object> condition
    ) {
        var map = new LinkedHashMap<String, Object>();
        map.put("stepId", id);
        map.put("fieldId", field);
        map.put("required", required);
        map.put("skippable", skippable);
        if (condition != null) map.put("condition", condition);
        return Map.copyOf(map);
    }

    private static Map<String, Object> in(String field, String... values) {
        return leaf(field, "IN", null, values);
    }

    private static Map<String, Object> selectedAny(String field, String... values) {
        return leaf(field, "SELECTED_ANY_IN", null, values);
    }

    private static Map<String, Object> leaf(String field, String operator, String expectedValue, String... values) {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", "LEAF");
        map.put("sourceFieldId", field);
        map.put("operator", operator);
        if (expectedValue != null) map.put("expectedValue", expectedValue);
        if (values.length > 0) map.put("expectedValues", List.of(values));
        return Map.copyOf(map);
    }

    private static Map<String, Object> not(Map<String, Object> condition) {
        return Map.of("type", "NOT", "item", condition);
    }
}
