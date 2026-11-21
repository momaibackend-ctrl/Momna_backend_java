package com.momna.modules.myday.application;

import java.util.List;
import java.util.Map;

/** The 9-card day of the generation spec (MOMNA-989 / LLM spec v1.0): 3 AM, 3 PM, 3 optional, 7 fields each. */
public final class MyDayCards {
    public static final List<String> SLOTS = List.of("AM", "PM", "OPTIONAL");
    public static final int CARDS_PER_SLOT = 3;

    private MyDayCards() {}

    public record Card(
        String slot,
        int position,
        String title,
        String microcopy,
        String whatToDo,
        String whyToday,
        String howToDo,
        String ifSkipped,
        String support
    ) {}

    /** What a card source produced, with the versions needed to reproduce it. */
    public record GeneratedDay(
        List<Card> cards,
        String cardSource,
        String generationPolicyVersion,
        String promptVersion,
        String modelRef,
        String safetyVersion,
        String cacheKey
    ) {
        public GeneratedDay {
            cards = List.copyOf(cards);
        }
    }

    /** Lifecycle period to the spec's life_mode. */
    public static final Map<String, String> LIFE_MODES = Map.of(
        "MENARCHE", "teen_menarche",
        "CYCLE", "adult_cycle",
        "PLANNING", "planning",
        "PREGNANCY", "pregnancy",
        "POSTPARTUM", "postpartum",
        "PERIMENOPAUSE", "perimenopause",
        "MENOPAUSE", "menopause"
    );

    /** Fails closed unless the day has exactly 3+3+3 cards with every field present. */
    public static void requireComplete(List<Card> cards) {
        if (cards.size() != SLOTS.size() * CARDS_PER_SLOT) {
            throw new MyDayException("MY_DAY_OUTPUT_INVALID", "My Day requires exactly 9 cards");
        }
        for (var slot : SLOTS) {
            var positions = cards.stream().filter(c -> slot.equals(c.slot())).map(Card::position).sorted().toList();
            if (!positions.equals(List.of(1, 2, 3))) {
                throw new MyDayException("MY_DAY_OUTPUT_INVALID", "My Day requires 3 cards in every slot");
            }
        }
        for (var card : cards) {
            for (var value : List.of(card.title(), card.microcopy(), card.whatToDo(), card.whyToday(),
                card.howToDo(), card.ifSkipped(), card.support())) {
                if (value == null || value.isBlank()) {
                    throw new MyDayException("MY_DAY_OUTPUT_INVALID", "Every My Day card field is required");
                }
            }
        }
    }
}
