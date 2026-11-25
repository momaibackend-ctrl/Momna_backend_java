package com.momna.modules.myday.template;

import static org.junit.jupiter.api.Assertions.*;

import com.momna.modules.myday.application.template.MyDayCalendarFacts;
import com.momna.modules.myday.application.template.MyDayFacts.OnboardingFact;
import com.momna.modules.myday.application.template.MyDayTemplateKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MyDayTemplateBoundaryTest {
    static final LocalDate DAY = LocalDate.parse("2026-11-23");

    /** The key's components are exactly the prompt inputs and versions: no place for a user, a session or text. */
    @Test
    void theTemplateKeyHasNoPersonalComponent() {
        var components = Arrays.stream(MyDayTemplateKey.class.getRecordComponents()).map(c -> c.getName())
            .collect(Collectors.toSet());
        assertEquals(Set.of("lifeMode", "dayNumber", "cycleState", "phaseCode", "weekNumber", "trimester",
            "overlayKeys", "userLanguage", "tone", "versions"), components);
        var key = new MyDayTemplateKey("adult_cycle", 12, "luteal", "adult_luteal", null, null,
            List.of("adult_pain_high", "adult_low_energy"), "ru", "close_friend", MyDayTemplateKey.Versions.current());
        assertEquals(List.of("adult_low_energy", "adult_pain_high"), key.overlayKeys());
        assertTrue(key.canonical().contains("purpose=MY_DAY"));
        assertTrue(key.canonical().contains("prompts=myday.prompts.v1.2"));
        assertTrue(key.canonical().contains("tone=close_friend"));
        assertEquals("ru", key.promptParameters().get("user_language"));
        assertEquals("close_friend", key.promptParameters().get("tone"));
    }

    @Test
    void anOverlayOfAnotherLifeModeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MyDayTemplateKey("adult_cycle", 1, "uncertain",
            "adult_uncertain", null, null, List.of("preg_nausea"), "en", null, MyDayTemplateKey.Versions.current()));
    }

    /** The template package reads results only: no scoring, safety engine, persistence, Spring or JPA. */
    @Test
    void theTemplatePackageDependsOnNothingButTheJdk() throws Exception {
        var dir = Path.of("src/main/java/com/momna/modules/myday/application/template");
        try (var files = Files.list(dir)) {
            for (var file : files.toList()) {
                for (var line : Files.readAllLines(file)) {
                    if (!line.startsWith("import ")) continue;
                    assertTrue(line.startsWith("import java.") || line.startsWith("import static java.")
                            || line.startsWith("import com.momna.modules.myday.application.template."),
                        file.getFileName() + ": " + line);
                }
            }
        }
    }

    /** The registry is the single list of codes: the adapter reads exactly what the migrations register. */
    @Test
    void theFeedingCodesAreTheRegisteredOnes() throws Exception {
        var v33 = Files.readString(Path.of("src/main/resources/db/liquibase/sql/V33__postpartum_feeding_codes.sql"));
        for (var code : com.momna.modules.myday.application.template.MyDayOverlayAdapter.FEEDING_CODES) {
            assertTrue(v33.contains('"' + code + '"'), code);
        }
        var v34 = Files.readString(Path.of("src/main/resources/db/liquibase/sql/V34__feeding_breast_milk_now_options.sql"));
        assertTrue(v34.contains("[\"yes\", \"no\", \"not_sure\", \"prefer_not_to_say\"]"));
    }

    @Test
    void onboardingAnswersAreReadInEveryStoredShape() {
        assertEquals(Set.of("school"), fact(Map.of("value", "school")).options());
        assertEquals(Set.of("school"), fact(Map.of("value", Map.of("kind", "ENUM", "value", "school"))).options());
        assertEquals(Set.of("a", "b"), fact(Map.of("value", List.of("a", "b"))).options());
        assertEquals(Set.of("a"), fact(Map.of("value", Map.of("a", true, "b", false))).options());
        assertEquals(Set.of("a"), fact(Map.of("value", Map.of("kind", "OBJECT", "value", Map.of("a", true)))).options());
        assertEquals("20", fact(Map.of("value", Map.of("kind", "NUMBER", "value", 20))).scalar().orElseThrow());
        assertTrue(fact(Map.of("value", List.of("a"))).scalar().isEmpty());
        // per child: the answers count, never the child references
        assertEquals(Set.of("nursing_at_breast", "infant_formula"),
            fact(Map.of("value", Map.of("child-1", "nursing_at_breast", "child-2", "infant_formula"))).options());
        assertEquals(Map.of("child-1", "nursing_at_breast", "child-2", "infant_formula"),
            fact(Map.of("value", Map.of("child-1", "nursing_at_breast", "child-2", "infant_formula"))).perChild());
        assertEquals(Map.of("*", "yes"), fact(Map.of("value", "yes")).perChild());
        assertEquals(Map.of("b", "no"), fact(Map.of("value", List.of(Map.of("childRef", "b", "value", "no")))).perChild());
        assertEquals(Set.of("combination_feeding"), fact(Map.of("value", List.of(
            Map.of("childRef", "child-1", "value", "combination_feeding")))).options());
    }

    @Test
    void reportedPregnancyWeeksAdvanceFromTheDayTheyWereGivenInTheUsersZone() {
        var zone = ZoneId.of("America/New_York");
        // 23:30 local on 01.11 is 02.11 in UTC: the day counts in her zone.
        var answered = Instant.parse("2026-11-02T04:30:00Z");
        var onboarding = Map.of("onboarding.pregnancy.gestational_weeks",
            new OnboardingFact(Map.of("value", 20), answered));
        var facts = MyDayCalendarFacts.derive("pregnancy", onboarding, DAY, DAY, zone);
        // 20w + 22 days = 23w1d
        assertEquals(23, facts.weekNumber());
        assertEquals("trimester_2", facts.trimester());
        assertEquals("preg_trimester2_w23", facts.phaseCode());
    }

    @Test
    void unknownFactsStayUnknown() {
        assertEquals(new MyDayCalendarFacts(1, "not_applicable", "preg_unknown", null, null),
            MyDayCalendarFacts.derive("pregnancy", Map.of(), DAY, null, ZoneId.of("UTC")));
        assertEquals(new MyDayCalendarFacts(31, "uncertain", "teen_uncertain", null, null),
            MyDayCalendarFacts.derive("teen_menarche", Map.of(), DAY, DAY.minusDays(30), ZoneId.of("UTC")));
        assertEquals("post_unknown", MyDayCalendarFacts.derive("postpartum", Map.of("onboarding.postpartum.birth_date",
            fact(Map.of("value", DAY.minusDays(400).toString()))), DAY, DAY, ZoneId.of("UTC")).phaseCode());
        assertEquals("meno_general", MyDayCalendarFacts.derive("menopause", Map.of(), DAY, DAY, ZoneId.of("UTC")).phaseCode());
        assertEquals("uncertain", MyDayCalendarFacts.derive("adult_cycle", Map.of("onboarding.cycle.lmp",
            fact(Map.of("value", "not-a-date"))), DAY, DAY, ZoneId.of("UTC")).cycleState());
    }

    @Test
    void theTrimesterBoundariesAreTheAcogOnes() {
        assertEquals("trimester_1", trimesterAt(13 * 7 + 6));
        assertEquals("trimester_2", trimesterAt(14 * 7));
        assertEquals("trimester_2", trimesterAt(27 * 7 + 6));
        assertEquals("trimester_3", trimesterAt(28 * 7));
    }

    private static String trimesterAt(int gestationDays) {
        var onboarding = Map.of("onboarding.pregnancy.lmp", fact(Map.of("value", DAY.minusDays(gestationDays).toString())));
        return MyDayCalendarFacts.derive("pregnancy", onboarding, DAY, DAY, ZoneId.of("UTC")).trimester();
    }

    private static OnboardingFact fact(Object payload) {
        return new OnboardingFact(payload, Instant.parse("2026-11-01T10:00:00Z"));
    }
}
