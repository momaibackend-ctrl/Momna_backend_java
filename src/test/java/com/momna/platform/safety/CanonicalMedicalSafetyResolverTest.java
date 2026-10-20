package com.momna.platform.safety;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class CanonicalMedicalSafetyResolverTest {
    private final MedicalSafetyCatalog catalog = new MedicalSafetyCatalog(new ObjectMapper());
    private final CanonicalSafetyService resolver = new CanonicalSafetyService();

    private CanonicalSafetyService.MedicalStep step(String route, Map<String, String> answers) {
        return resolver.evaluateMedical(catalog.route(route), catalog, answers, Map.of(), false);
    }

    @Test
    void immediateDangerStopsAtFirstYes() {
        var result = step("R1", Map.of("F_THUNDERCLAP_HEADACHE", "YES"));
        assertEquals("EMERGENCY", result.disposition());
        assertEquals("E", result.screenCode());
        assertEquals("COMPLETE", result.state());
    }

    @Test
    void postpartumMentalHealthChecksHarmBeforeOtherFacts() {
        assertEquals("F_HARM_THOUGHTS", step("R18", Map.of()).nextFactCode());
        assertEquals("PM2", step("R18", Map.of("F_HARM_THOUGHTS", "YES")).screenCode());
    }

    @Test
    void bleedingUrgencyDoesNotHidePotentialShockOrEctopicCombination() {
        var answers = new LinkedHashMap<String, String>();
        answers.put("F_CHEST_PAIN", "NO");
        answers.put("F_BREATHING_SEVERE", "NO");
        answers.put("F_FAINTED", "NO");
        answers.put("F_PAD_SOAK_1H", "YES");
        assertEquals("F_SEVERE_ABDO_PELVIC_PAIN", step("R4", answers).nextFactCode());
        answers.put("F_SEVERE_ABDO_PELVIC_PAIN", "YES");
        assertEquals("F_PREGNANCY_POSSIBLE", step("R4", answers).nextFactCode());
        answers.put("F_PREGNANCY_POSSIBLE", "UNSURE");
        assertEquals("F_PRESYNCOPE", step("R4", answers).nextFactCode());
        answers.put("F_PRESYNCOPE", "YES");
        assertEquals("EMERGENCY", step("R4", answers).disposition());
    }

    @Test
    void planningTreatsPregnancyAsPossibleWithoutAnotherQuestion() {
        var result = step("R28", Map.of("F_SEVERE_ABDO_PELVIC_PAIN", "YES"));
        assertEquals("F_CHEST_PAIN", result.nextFactCode());
        var answers = Map.of(
            "F_CHEST_PAIN","NO", "F_BREATHING_SEVERE","NO", "F_FAINTED","NO",
            "F_PAD_SOAK_1H","NO", "F_SEVERE_ABDO_PELVIC_PAIN","YES",
            "F_PRESYNCOPE","NO"
        );
        assertEquals("EP1", step("R28", answers).screenCode());
    }

    @Test
    void r17DoesNotSkipBpAfterUrgentHeadache() {
        var answers = Map.of(
            "F_BREATHING_SEVERE","NO", "F_THUNDERCLAP_HEADACHE","NO",
            "F_NEURO_DEFICIT","NO", "F_SEVERE_HEADACHE","YES",
            "F_VISION_CHANGE","NO", "F_UPPER_ABDO_PAIN","NO"
        );
        assertEquals("F_BP_MEASURED", step("R17", answers).nextFactCode());
        var more = new HashMap<>(answers);
        more.put("F_BP_MEASURED","YES");
        assertEquals("BP1", step("R17", more).screenCode());
    }

    @Test
    void anaphylaxisSelectsAg1AndUnknownNeverTurnsIntoNo() {
        assertEquals("AG1", step("R25", Map.of("F_AIRWAY_SWELLING","YES")).screenCode());
        var result = step("R1", Map.of("F_THUNDERCLAP_HEADACHE","UNSURE",
            "F_NEURO_DEFICIT","NO", "F_VISION_CHANGE","NO"));
        assertEquals("N", result.screenCode());
        assertEquals("UNKNOWN", result.disposition());
    }
}
