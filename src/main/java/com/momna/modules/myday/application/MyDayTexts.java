package com.momna.modules.myday.application;

import java.util.List;
import java.util.Map;

/**
 * Server-owned texts of the My Day screens (app decision D-2: every text inside a card is data).
 * English follows the implemented design (Figma 4q6z6gWxqUDMgHRJjH4x8N, Adult Cycle frames A, L, P, B
 * and the "Last Night" variant of A, checked against the export of 24.11.2026). Russian is a draft
 * awaiting Product approval. Button labels stay in the app.
 *
 * <p>Some texts name the life period ("Recommendations for a regular cycle."). Only the Adult Cycle
 * wording is in the design export so far; for the other periods the period-specific texts are absent
 * (no subtitle) or the neutral wording is used, never an invented one, until their frames are exported.
 */
public final class MyDayTexts {
    public static final String VERSION = "myday.texts.v2";

    private MyDayTexts() {}

    public record Choice(String title, String description) {}

    public record Set(
        Choice personal,
        Choice general,
        Choice fromEvening,
        String personalBadge,
        List<String> sectionHeaders,
        String noteBadge,
        String generationFailure,
        /** Screen L while the plan is being made. */
        Choice generating,
        /** The My Day header subtitle per lifecycle period, e.g. CYCLE: "Recommendations for a regular cycle." */
        Map<String, String> subtitleByPeriod,
        /** The "Standard" card description per lifecycle period; {@link #general} is the neutral fallback. */
        Map<String, String> generalByPeriod
    ) {
        /** The header subtitle of this period, or null when the design has none yet. */
        public String subtitle(String lifecyclePeriod) {
            return lifecyclePeriod == null ? null : subtitleByPeriod.get(lifecyclePeriod);
        }

        /** The "Standard" card with this period's description when the design has one. */
        public Choice general(String lifecyclePeriod) {
            var description = lifecyclePeriod == null ? null : generalByPeriod.get(lifecyclePeriod);
            return description == null ? general : new Choice(general.title(), description);
        }
    }

    private static final Set EN = new Set(
        new Choice("Personal", "Answer a few questions — you'll get recommendations tailored to how you feel today."),
        new Choice("Standard", "General recommendations — no questions."),
        new Choice("Last Night", "Your plan, shaped by how you felt last night."),
        "Personal",
        List.of("WHAT TO DO", "WHY TODAY", "HOW TO DO IT", "IF IT DIDN'T WORK OUT"),
        "A NOTE FROM MOMNA",
        "We couldn't prepare your plan. Please try again.",
        new Choice("Getting your plan ready", "I'm turning your answers into today's recommendations."),
        Map.of("CYCLE", "Recommendations for a regular cycle."),
        Map.of("CYCLE", "General recommendations for a regular cycle — no questions.")
    );

    private static final Set RU = new Set(
        new Choice("Персональный", "Ответьте на несколько вопросов — и рекомендации учтут, как вы себя чувствуете сегодня."),
        new Choice("Общий", "Общие рекомендации — без вопросов."),
        new Choice("Вчера вечером", "Ваш план — по тому, как вы чувствовали себя вчера вечером."),
        "Персональный",
        List.of("ЧТО СДЕЛАТЬ", "ПОЧЕМУ СЕГОДНЯ", "КАК СДЕЛАТЬ", "ЕСЛИ НЕ ПОЛУЧИЛОСЬ"),
        "ЗАМЕТКА ОТ MOMNA",
        "Не получилось подготовить план. Попробуйте ещё раз.",
        new Choice("Готовим ваш план", "Превращаю ваши ответы в рекомендации на сегодня."),
        Map.of("CYCLE", "Рекомендации для регулярного цикла."),
        Map.of("CYCLE", "Общие рекомендации для регулярного цикла — без вопросов.")
    );

    private static final Map<String, Set> BY_LANGUAGE = Map.of("en", EN, "ru", RU);

    public static Set of(String language) {
        return BY_LANGUAGE.getOrDefault(language, EN);
    }
}
