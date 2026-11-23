package com.momna.modules.myday;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.momna.modules.myday.application.MyDayCardSetParser;
import com.momna.modules.myday.application.MyDayException;
import java.util.List;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/** The output contract of the My Day generation spec v1.0: 3+3+3 cards, exactly 7 fields each. */
class MyDayCardSetParserTest {
    private static final List<String> FIELDS = List.of(
        "title", "microcopy", "what_to_do", "why_today", "how_to_do", "if_skipped", "support");
    private final ObjectMapper mapper = new ObjectMapper();
    private final MyDayCardSetParser parser = new MyDayCardSetParser(mapper);

    @Test
    void validSpecOutputBecomesNineOrderedCards() {
        var cards = parser.parse(valid().toString());
        assertEquals(9, cards.size());
        assertEquals(List.of("AM", "AM", "AM", "PM", "PM", "PM", "OPTIONAL", "OPTIONAL", "OPTIONAL"),
            cards.stream().map(c -> c.slot()).toList());
        assertEquals(List.of(1, 2, 3, 1, 2, 3, 1, 2, 3), cards.stream().map(c -> c.position()).toList());
        assertEquals("evening 2 title", cards.get(4).title());
        assertEquals("evening 2 support", cards.get(4).support());
    }

    @Test
    void malformedOutputsAreRejected() {
        assertInvalid("not json");
        assertInvalid("[]");
        var missingGroup = valid(); missingGroup.remove("optional"); assertInvalid(missingGroup.toString());
        var extraGroup = valid(); extraGroup.putArray("night"); assertInvalid(extraGroup.toString());
        var twoCards = valid(); twoCards.withArray("morning").remove(2); assertInvalid(twoCards.toString());
        var missingField = valid(); ((ObjectNode) missingField.withArray("evening").get(0)).remove("support");
        assertInvalid(missingField.toString());
        var extraField = valid(); ((ObjectNode) extraField.withArray("evening").get(0)).put("diagnosis", "x");
        assertInvalid(extraField.toString());
        var blank = valid(); ((ObjectNode) blank.withArray("optional").get(1)).put("why_today", "  ");
        assertInvalid(blank.toString());
        var number = valid(); ((ObjectNode) number.withArray("optional").get(1)).put("title", 7);
        assertInvalid(number.toString());
        var longTitle = valid(); ((ObjectNode) longTitle.withArray("morning").get(0))
            .put("title", "one two three four five six seven eight");
        assertInvalid(longTitle.toString());
    }

    @Property(tries = 300)
    void removingOrBlankingAnyFieldOfAnyCardIsRejected(
        @ForAll @IntRange(min = 0, max = 2) int group,
        @ForAll @IntRange(min = 0, max = 2) int card,
        @ForAll @IntRange(min = 0, max = 6) int field,
        @ForAll boolean remove
    ) {
        var root = valid();
        var target = (ObjectNode) root.withArray(List.of("morning", "evening", "optional").get(group)).get(card);
        if (remove) target.remove(FIELDS.get(field)); else target.put(FIELDS.get(field), "");
        assertInvalid(root.toString());
    }

    @Property(tries = 200)
    void anyWellFormedSetParsesToItsOwnContent(@ForAll("words") String word) {
        var root = valid();
        ((ObjectNode) root.withArray("optional").get(2)).put("microcopy", word);
        var cards = parser.parse(root.toString());
        assertEquals(word.trim(), cards.get(8).microcopy());
        assertEquals(9, cards.size());
    }

    @Provide
    Arbitrary<String> words() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(40);
    }

    private void assertInvalid(String text) {
        assertEquals("MY_DAY_OUTPUT_INVALID", assertThrows(MyDayException.class, () -> parser.parse(text)).code());
    }

    private ObjectNode valid() {
        var root = mapper.createObjectNode();
        for (var group : List.of("morning", "evening", "optional")) {
            var array = root.putArray(group);
            for (int i = 1; i <= 3; i++) {
                var card = array.addObject();
                for (var field : FIELDS) card.put(field, group + " " + i + " " + field);
            }
        }
        return root;
    }
}
