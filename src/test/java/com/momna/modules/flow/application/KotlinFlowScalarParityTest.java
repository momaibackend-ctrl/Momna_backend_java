package com.momna.modules.flow.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Executable Kotlin FlowPredicateEvaluator legacy OBJECT/NUMBER equality invariants. */
class KotlinFlowScalarParityTest {
    private FlowDefinitionModel.Condition leaf(String operation, String expected) {
        return new FlowDefinitionModel.Condition(
            "LEAF", "payload", operation, expected, Set.of(), List.of(), null
        );
    }

    @Test
    void objectEqualityUsesStableLexicalKeyOrder() {
        var values = Map.of("payload", Map.<String, Object>of(
            "value", Map.of("kind", "OBJECT", "value", Map.of("b", "2", "a", "1"))
        ));
        assertTrue(FlowConditionEvaluator.matches(leaf("EQUALS", "a=1|b=2"), values));
        assertFalse(FlowConditionEvaluator.matches(leaf("NOT_EQUALS", "a=1|b=2"), values));
    }

    @Test
    void numberScalarStripsTrailingDecimalZerosLikeKotlin() {
        var values = Map.of("payload", Map.<String, Object>of(
            "value", Map.of("kind", "NUMBER", "value", 19.00)
        ));
        assertTrue(FlowConditionEvaluator.matches(leaf("EQUALS", "19"), values));
        assertTrue(FlowConditionEvaluator.matches(leaf("NUMBER_GTE", "18"), values));
    }
}
