package com.momna.modules.checkin.definition;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.application.CheckinItemTexts;
import com.momna.modules.checkin.domain.CheckinDefinitionPeriod;
import com.momna.modules.checkin.domain.CheckinPhase;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Question and answer texts with their tones (app plan.md D-2, D-3, D-10) for every Check-in item. */
class CheckinItemTextsTest {
    private final WeightedCheckinDefinitionCatalog catalog = new WeightedCheckinDefinitionCatalog(new ObjectMapper());

    @Test
    void theAdultCycleMorningMatchesTheDesign() {
        var items = catalog.latest(CheckinDefinitionPeriod.CYCLE, CheckinPhase.MORNING).items();
        assertEquals(List.of("Morning state", "Sleep & energy", "Pain or cramps", "Bleeding & discomfort",
            "Bloating / heaviness", "Headache", "Mood / stress", "Day's workload", "Appetite"),
            items.stream().map(i -> CheckinItemTexts.title(i, "en")).toList());
        var first = CheckinItemTexts.options(items.getFirst(), "en");
        assertEquals(List.of(new CheckinItemTexts.Option(0, "As usual", "GOOD"),
            new CheckinItemTexts.Option(1, "Slightly off", "NOT_GOOD"),
            new CheckinItemTexts.Option(2, "Noticeably off", "BAD")), first);
        var workload = items.stream().filter(i -> i.itemCode().equals("C8")).findFirst().orElseThrow();
        assertEquals(List.of(new CheckinItemTexts.Option(0, "Light day", "GOOD"),
            new CheckinItemTexts.Option(1, "Ordinary", "NEUTRAL"),
            new CheckinItemTexts.Option(2, "Very packed", "NOT_GOOD")), CheckinItemTexts.options(workload, "en"));
        assertNotEquals(CheckinItemTexts.title(items.getFirst(), "en"), CheckinItemTexts.title(items.getFirst(), "ru"));
        assertTrue(Character.isUpperCase(CheckinItemTexts.options(items.getFirst(), "ru").getFirst().text().charAt(0)));
    }

    @Test
    void everyItemOfEveryPeriodHasATitleAndThreeTonedAnswersInBothLanguages() {
        var tones = Set.of("GOOD", "NOT_GOOD", "BAD", "NEUTRAL");
        int items = 0;
        for (var definition : catalog.all()) {
            for (var item : definition.items()) {
                items++;
                for (var language : List.of("en", "ru")) {
                    assertFalse(CheckinItemTexts.title(item, language).isBlank(), item.itemCode());
                    var options = CheckinItemTexts.options(item, language);
                    assertEquals(List.of(0, 1, 2), options.stream().map(CheckinItemTexts.Option::value).toList(),
                        item.itemCode());
                    for (var option : options) {
                        assertFalse(option.text().isBlank(), item.itemCode());
                        assertTrue(Character.isUpperCase(option.text().charAt(0)) || !Character.isLetter(option.text().charAt(0)),
                            item.itemCode() + " " + option.text());
                        assertTrue(tones.contains(option.tone()), item.itemCode());
                    }
                }
            }
        }
        assertEquals(102, items);
    }

    @Test
    void scalesThatAreNotBetterToWorseHaveTheirOwnTones() {
        assertEquals(List.of("GOOD", "BAD", "NEUTRAL"), tones(CheckinDefinitionPeriod.POSTPARTUM, CheckinPhase.MORNING, "PP2"),
            "fine, emotionally hard, can't tell yet");
        assertEquals(List.of("NEUTRAL", "NEUTRAL", "NEUTRAL"), tones(CheckinDefinitionPeriod.PLANNING, CheckinPhase.MORNING, "T2"));
        assertEquals(List.of("GOOD", "NEUTRAL", "BAD"), tones(CheckinDefinitionPeriod.CYCLE, CheckinPhase.EVENING, "C10"));
        assertEquals(List.of("NEUTRAL", "GOOD", "BAD"), tones(CheckinDefinitionPeriod.PERIMENOPAUSE, CheckinPhase.EVENING, "PM7"));
        assertEquals(List.of("GOOD", "NOT_GOOD", "BAD"), tones(CheckinDefinitionPeriod.PREGNANCY, CheckinPhase.MORNING, "P19"));
    }

    @Test
    void everyToneRowNamesARealItem() {
        var codes = catalog.all().stream().flatMap(d -> d.items().stream())
            .map(WeightedCheckinDefinitionCatalog.Item::itemCode).collect(java.util.stream.Collectors.toSet());
        assertTrue(codes.containsAll(CheckinItemTexts.itemsWithOwnTones()), CheckinItemTexts.itemsWithOwnTones().toString());
    }

    private List<String> tones(CheckinDefinitionPeriod period, CheckinPhase phase, String code) {
        var item = catalog.latest(period, phase).items().stream().filter(i -> i.itemCode().equals(code)).findFirst()
            .orElseThrow();
        return CheckinItemTexts.options(item, "en").stream().map(CheckinItemTexts.Option::tone).toList();
    }
}
