package com.momna.modules.flow;

import static org.junit.jupiter.api.Assertions.*;

import com.momna.modules.flow.application.FlowAnswerSpec;
import com.momna.modules.flow.application.FlowException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ONB-API-01: an onboarding answer is checked against its canonical registry entry. */
class FlowAnswerSpecTest {
    private static final FlowAnswerSpec PARTNER = FlowAnswerSpec.of("ENUM",
        Map.of("allowedValues", List.of("one_steady", "no_partner", "prefer_not_to_say")));
    private static final FlowAnswerSpec GOALS = FlowAnswerSpec.of("OBJECT",
        Map.of("allowedKeys", List.of("know_next_period", "fertile_days")));
    private static final FlowAnswerSpec BEHAVIOR = FlowAnswerSpec.of("OBJECT",
        Map.of("allowedKeys", List.of("planning", "depth"), "requiredKeys", List.of("planning", "depth"),
            "valuesByKey", Map.of("planning", List.of("plan_ahead", "as_i_go"), "depth", List.of("action_first", "explain_first"))));
    private static final FlowAnswerSpec FEEDING = FlowAnswerSpec.of("OBJECT",
        Map.of("allowedValues", List.of("nursing_at_breast", "infant_formula"), "perChild", true));

    private static Map<String, Object> answer(Object value) {
        return Map.of("kind", "X", "value", value);
    }

    private static void rejected(FlowAnswerSpec spec, Map<String, Object> payload) {
        var refused = assertThrows(FlowException.class, () -> spec.validate(payload));
        assertEquals("VALIDATION", refused.code());
    }

    @Test
    void anEnumTakesOneOfItsValues() {
        PARTNER.validate(answer("one_steady"));
        rejected(PARTNER, answer("no"));
        rejected(PARTNER, answer(List.of("one_steady")));
        assertEquals(List.of("one_steady", "no_partner", "prefer_not_to_say"), PARTNER.options());
    }

    @Test
    void anObjectTakesAListOrAMapOfItsKeys() {
        GOALS.validate(answer(List.of("know_next_period", "fertile_days")));
        GOALS.validate(answer(Map.of("fertile_days", true)));
        GOALS.validate(Map.of("selected", List.of("fertile_days")));
        rejected(GOALS, answer(List.of("know_next_period", "no")));
        rejected(GOALS, answer(List.of()));
        rejected(GOALS, answer("no"));
    }

    @Test
    void requiredKeysMustAllBeAnswered() {
        BEHAVIOR.validate(answer(Map.of("planning", "plan_ahead", "depth", "explain_first")));
        rejected(BEHAVIOR, answer(Map.of("planning", "plan_ahead", "depth", "brief")));
        rejected(BEHAVIOR, answer(Map.of("planning", "plan_ahead")));
        rejected(BEHAVIOR, answer(List.of("planning", "depth")));
    }

    @Test
    void aPerChildAnswerHasAnAllowedValueForEveryChild() {
        FEEDING.validate(answer(Map.of("1", "nursing_at_breast", "2", "infant_formula")));
        FEEDING.validate(Map.of("by_child", Map.of("1", "infant_formula")));
        rejected(FEEDING, answer(Map.of("1", "breast")));
        rejected(FEEDING, answer(Map.of()));
    }

    @Test
    void datesNumbersAndTextsStayInTheirRange() {
        var lastPeriod = FlowAnswerSpec.of("DATE", Map.of("minDaysFromNow", -365, "maxDaysFromNow", 0));
        var today = LocalDate.now(ZoneOffset.UTC);
        lastPeriod.validate(answer(today.minusDays(3).toString()));
        lastPeriod.validate(Map.of("date", today.toString()));
        rejected(lastPeriod, answer(today.plusDays(5).toString()));
        rejected(lastPeriod, answer(today.minusDays(400).toString()));
        rejected(lastPeriod, answer("no"));

        var weeks = FlowAnswerSpec.of("NUMBER", Map.of("minimum", 1, "maximum", 42));
        weeks.validate(answer(12));
        weeks.validate(answer("12"));
        rejected(weeks, answer(43));
        rejected(weeks, answer("soon"));

        var detail = FlowAnswerSpec.of("TEXT", Map.of("maxLength", 5));
        detail.validate(answer("nurse"));
        rejected(detail, answer("teacher"));
        rejected(detail, answer(3));

        var country = FlowAnswerSpec.of("TEXT", Map.of("maxLength", 2, "pattern", "^[A-Z]{2}$"));
        country.validate(answer("PL"));
        rejected(country, answer("pl"));
        rejected(country, answer("POL"));
    }

    @Test
    void aFieldWithoutListedValuesAcceptsAnyValueOfItsShape() {
        FlowAnswerSpec.UNKNOWN.validate(answer("anything"));
        FlowAnswerSpec.of("ENUM", Map.of()).validate(answer("not_started"));
        FlowAnswerSpec.of("OBJECT", Map.of()).validate(answer(Map.of("1", "home")));
        assertNull(FlowAnswerSpec.of("ENUM", Map.of()).options());
    }
}
