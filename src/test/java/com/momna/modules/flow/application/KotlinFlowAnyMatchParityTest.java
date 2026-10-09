package com.momna.modules.flow.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Ports Kotlin FlowPredicateEvaluatorTest's AnyMatch behavioural cases.
 * AnyMatch must range over collection values, not scalar/typed wrappers.
 */
class KotlinFlowAnyMatchParityTest {
    private FlowDefinitionModel.Condition leaf(String operator, Set<String> values) {
        return new FlowDefinitionModel.Condition(
            "LEAF", "element", operator, null, values, List.of(), null
        );
    }

    private FlowDefinitionModel.Condition anyMatch(String operator, Set<String> values) {
        return new FlowDefinitionModel.Condition(
            "ANY_MATCH", "children", null, null, Set.of(),
            List.of(leaf(operator, values)), null
        );
    }

    private Map<String, Map<String, Object>> children(String... statuses) {
        var values = new java.util.LinkedHashMap<String, Object>();
        for (var i = 0; i < statuses.length; i++) {
            values.put(Integer.toString(i + 1), statuses[i]);
        }
        return Map.of("children", Map.of("value", values));
    }

    @Test
    void anyMatchIsExistentialAcrossChildren() {
        var condition = anyMatch("IN", Set.of("hospital", "both"));
        assertTrue(FlowConditionEvaluator.matches(condition, children("hospital")));
        assertFalse(FlowConditionEvaluator.matches(condition, children("home")));
        assertTrue(FlowConditionEvaluator.matches(condition, children("home", "hospital", "home")));
        assertFalse(FlowConditionEvaluator.matches(condition, children("home", "not_with_me")));
    }

    @Test
    void anyMatchFailsClosedForMissingEmptyReferenceAndScalarValues() {
        var condition = anyMatch("IN", Set.of("hospital"));
        assertFalse(FlowConditionEvaluator.matches(condition, Map.of()));
        assertFalse(FlowConditionEvaluator.matches(condition, children()));
        assertFalse(FlowConditionEvaluator.matches(condition, Map.of(
            "children", Map.of("reference", Map.of("provider", "test", "id", "ref-1"))
        )));
        assertFalse(FlowConditionEvaluator.matches(condition, Map.of(
            "children", Map.of("value", Map.of("kind", "ENUM", "value", "hospital"))
        )), "ENUM scalar must not be treated as collection elements");
    }

    @Test
    void anyMatchComposesWithAllAndNot() {
        var deceased = anyMatch("IN", Set.of("deceased"));
        var survived = anyMatch("IN", Set.of("home", "hospital"));
        var both = new FlowDefinitionModel.Condition(
            "ALL", null, null, null, Set.of(), List.of(deceased, survived), null
        );
        var notDeceased = new FlowDefinitionModel.Condition(
            "NOT", null, null, null, Set.of(), List.of(deceased), null
        );
        assertTrue(FlowConditionEvaluator.matches(both, children("deceased", "home")));
        assertFalse(FlowConditionEvaluator.matches(both, children("deceased", "deceased")));
        assertFalse(FlowConditionEvaluator.matches(notDeceased, children("deceased")));
        assertTrue(FlowConditionEvaluator.matches(notDeceased, children("home")));
    }

    @Test
    void missingValuesDoNotPassNotIn() {
        var notIn = new FlowDefinitionModel.Condition(
            "LEAF", "missing", "NOT_IN", null, Set.of("hospital"), List.of(), null
        );
        assertFalse(FlowConditionEvaluator.matches(notIn, Map.of()));
    }
}
