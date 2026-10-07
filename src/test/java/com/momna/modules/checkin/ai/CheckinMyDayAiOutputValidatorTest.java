package com.momna.modules.checkin.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog;
import org.junit.jupiter.api.Test;

class CheckinMyDayAiOutputValidatorTest {
    private CheckinMyDayAiOutputValidator validator() {
        var mapper = new ObjectMapper();
        return new CheckinMyDayAiOutputValidator(
            mapper,
            new WeightedCheckinDefinitionCatalog(mapper)
        );
    }

    @Test
    void acceptsStrictPublicSchema() {
        var result = validator().validate(
            "{"schemaVersion":1,"headline":"Today","summary":"Take it steadily","actions":["Rest when needed"]}"
        );
        assertTrue(result.valid());
        assertEquals("OK", result.reason());
    }

    @Test
    void rejectsInternalPayloadLeak() {
        var result = validator().validate(
            "{"schemaVersion":1,"headline":"Today","summary":"raw_answer is high","actions":[]}"
        );
        assertFalse(result.valid());
        assertEquals("INTERNAL_PAYLOAD_LEAK", result.reason());
    }

    @Test
    void rejectsUnsupportedHormonalCausality() {
        var result = validator().validate(
            "{"schemaVersion":1,"headline":"Today","summary":"Hormonal change caused this symptom","actions":[]}"
        );
        assertFalse(result.valid());
        assertEquals("UNSUPPORTED_HORMONAL_CAUSALITY", result.reason());
    }
}
