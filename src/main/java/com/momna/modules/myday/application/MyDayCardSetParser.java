package com.momna.modules.myday.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses the generation spec's JSON output ({"morning":[3],"evening":[3],"optional":[3]},
 * each card with exactly title, microcopy, what_to_do, why_today, how_to_do, if_skipped, support).
 * Anything else is MY_DAY_OUTPUT_INVALID: the spec says an invalid answer is never cached or shown.
 */
public final class MyDayCardSetParser {
    private static final Map<String, String> GROUPS = Map.of("morning", "AM", "evening", "PM", "optional", "OPTIONAL");
    private static final Set<String> FIELDS = Set.of(
        "title", "microcopy", "what_to_do", "why_today", "how_to_do", "if_skipped", "support");
    private static final int MAX_TITLE_WORDS = 7;

    private final ObjectMapper mapper;

    public MyDayCardSetParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<MyDayCards.Card> parse(String text) {
        JsonNode root;
        try {
            root = mapper.readTree(text);
        } catch (Exception failure) {
            throw invalid("My Day output is not JSON");
        }
        if (root == null || !root.isObject() || !names(root).equals(GROUPS.keySet())) {
            throw invalid("My Day output must contain exactly morning, evening and optional");
        }
        var cards = new ArrayList<MyDayCards.Card>();
        for (var group : List.of("morning", "evening", "optional")) {
            var items = root.get(group);
            if (!items.isArray() || items.size() != MyDayCards.CARDS_PER_SLOT) {
                throw invalid("Every My Day group must have exactly 3 cards");
            }
            for (int index = 0; index < items.size(); index++) {
                var card = items.get(index);
                if (!card.isObject() || !names(card).equals(FIELDS)) {
                    throw invalid("Every My Day card must have exactly the 7 spec fields");
                }
                for (var field : FIELDS) {
                    if (!card.get(field).isTextual() || card.get(field).asText().isBlank()) {
                        throw invalid("My Day card fields must be non-empty text");
                    }
                }
                var title = card.get("title").asText().trim();
                if (title.split("\\s+").length > MAX_TITLE_WORDS) {
                    throw invalid("My Day card title exceeds 7 words");
                }
                cards.add(new MyDayCards.Card(GROUPS.get(group), index + 1, title,
                    text(card, "microcopy"), text(card, "what_to_do"), text(card, "why_today"),
                    text(card, "how_to_do"), text(card, "if_skipped"), text(card, "support")));
            }
        }
        MyDayCards.requireComplete(cards);
        return List.copyOf(cards);
    }

    private static String text(JsonNode card, String field) {
        return card.get(field).asText().trim();
    }

    private static Set<String> names(JsonNode node) {
        var names = new HashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static MyDayException invalid(String message) {
        return new MyDayException("MY_DAY_OUTPUT_INVALID", message);
    }
}
