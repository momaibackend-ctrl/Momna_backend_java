package com.momna.modules.flow.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlowConditionEvaluatorTest {
    @Test
    void evaluatesLeafAndNestedConditionsAgainstCanonicalPayloads() {
        var values = Map.of(
            "cycle", Map.<String,Object>of("value", Map.of("kind", "ENUM", "value", "pregnant")),
            "flags", Map.<String,Object>of("value", Map.of(
                "kind", "OBJECT",
                "value", Map.of("a", true, "b", false)
            ))
        );

        var cycle = leaf("cycle", "IN", null, Set.of("pregnant", "possible"));
        var selected = leaf("flags", "SELECTED_ANY_IN", null, Set.of("a"));

        var all = new FlowDefinitionModel.Condition(
            "ALL", null, null, null, Set.of(), List.of(cycle, selected), null
        );

        assertTrue(FlowConditionEvaluator.matches(all, values));
    }

    @Test
    void treatsMissingPayloadAsAbsentAndDeactivatesPresentPredicate() {
        var absent = leaf("optional", "ABSENT", null, Set.of());
        var present = leaf("optional", "PRESENT", null, Set.of());

        assertTrue(FlowConditionEvaluator.matches(absent, Map.of()));
        assertFalse(FlowConditionEvaluator.matches(present, Map.of()));
    }

    @Test
    void supportsNumericAndAnyMatchPredicates() {
        var values = Map.of(
            "context:age", Map.<String,Object>of("value", Map.of("kind", "NUMBER", "value", 21)),
            "children", Map.<String,Object>of("value", Map.of("first", "home", "second", "hospital"))
        );

        var adult = leaf("context:age", "NUMBER_GTE", "18", Set.of());
        var element = leaf("element", "IN", null, Set.of("hospital"));
        var anyMatch = new FlowDefinitionModel.Condition(
            "ANY_MATCH", "children", null, null, Set.of(), List.of(element), null
        );

        assertTrue(FlowConditionEvaluator.matches(adult, values));
        assertTrue(FlowConditionEvaluator.matches(anyMatch, values));
    }

    private FlowDefinitionModel.Condition leaf(
        String field,
        String operator,
        String expected,
        Set<String> values
    ) {
        return new FlowDefinitionModel.Condition(
            "LEAF", field, operator, expected, values, List.of(), null
        );
    }
}
