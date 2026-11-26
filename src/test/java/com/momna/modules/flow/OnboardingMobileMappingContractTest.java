package com.momna.modules.flow;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.flow.application.FlowDefinitionModel;
import com.momna.modules.flow.bootstrap.StandardFlowDefinitionCatalog;
import com.momna.modules.flow.infrastructure.FlowDefinitionEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * contracts/onboarding-mobile-mapping.json is the bridge from the app's onboarding screens to the server's fields
 * (ONB-API-01, MOMNA-1332): every field it names is a step of its flow, and every step the user answers has an
 * entry. The codes are checked against the field registry in AuthenticatedApiHttpE2eTest.
 */
class OnboardingMobileMappingContractTest {
    private static JsonNode mapping() throws Exception {
        return new ObjectMapper().readTree(Files.readString(Path.of("contracts/onboarding-mobile-mapping.json")));
    }

    @Test
    void everyFlowStepHasAnEntryAndEveryEntryIsAStep() throws Exception {
        var flows = mapping().path("flows");
        for (var spec : StandardFlowDefinitionCatalog.all()) {
            var model = FlowDefinitionModel.from(new FlowDefinitionEntity(spec.key(), spec.version(), spec.type(), null,
                spec.period(), null, spec.definitionJson(), 1, Instant.parse("2026-11-26T00:00:00Z")));
            var steps = new HashSet<String>();
            model.steps().forEach(s -> steps.add(s.fieldId()));
            var mapped = new HashSet<String>();
            for (var entry : flows.path(spec.key()).path("screens")) {
                mapped.add(entry.path("fieldId").asText());
                if (entry.has("freeText")) mapped.add(entry.path("freeText").path("fieldId").asText());
            }
            assertFalse(mapped.isEmpty(), spec.key() + " has no mapping");
            var unknown = new HashSet<>(mapped);
            unknown.removeAll(steps);
            assertTrue(unknown.isEmpty(), spec.key() + " maps fields it does not ask: " + unknown);
            var unmapped = new HashSet<>(steps);
            unmapped.removeAll(mapped);
            assertTrue(unmapped.isEmpty(), spec.key() + " asks fields the app has no entry for: " + unmapped);
        }
    }

    @Test
    void everyOptionHasACodeAndCodesAreUniquePerScreen() throws Exception {
        for (var flow : mapping().path("flows")) {
            for (var entry : flow.path("screens")) {
                var codes = new HashSet<String>();
                for (var option : entry.path("options")) {
                    var code = option.path("code").asText(null);
                    assertNotNull(code, entry.path("fieldId") + ": " + option);
                    assertTrue(codes.add(code), entry.path("fieldId") + " uses " + code + " twice");
                }
                for (var group : entry.path("groups")) {
                    assertEquals(2, group.path("choices").size(), entry.path("fieldId") + " " + group);
                }
            }
        }
    }

    @Test
    void theRulesSayCodesNotPositions() throws Exception {
        var rules = mapping().path("rules").toString();
        assertTrue(rules.contains("never by its position"), rules);
        assertEquals(Set.of("ENUM", "OBJECT_LIST", "OBJECT_GROUPS", "OBJECT_PER_CHILD", "DATE", "NUMBER", "TEXT"),
            new HashSet<>(iterable(mapping().path("answerShapes").fieldNames())));
    }

    private static <T> java.util.List<T> iterable(java.util.Iterator<T> it) {
        var out = new java.util.ArrayList<T>();
        it.forEachRemaining(out::add);
        return out;
    }
}
