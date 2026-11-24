package com.momna.modules.myday.application;

import java.util.List;
import java.util.Map;

/**
 * Server-owned texts of the My Day screens (app decision D-2: every text inside a card is data).
 * English follows the implemented design (Figma 4q6z6gWxqUDMgHRJjH4x8N, the app's MyDayFixtures);
 * Russian is a draft awaiting Product approval, like the FROM_EVENING_CHECKIN card, whose design
 * variant is not drawn yet (MOMNA-989 section 11). Button labels stay in the app.
 */
public final class MyDayTexts {
    public static final String VERSION = "myday.texts.v1";

    private MyDayTexts() {}

    public record Choice(String title, String description) {}

    public record Set(
        Choice personal,
        Choice general,
        Choice fromEvening,
        String personalBadge,
        List<String> sectionHeaders,
        String noteBadge,
        String generationFailure
    ) {}

    private static final Set EN = new Set(
        new Choice("Personal", "Answer a few questions — you'll get recommendations tailored to how you feel today."),
        new Choice("Standard", "General recommendations — no questions."),
        new Choice("Last Night", "Your plan, shaped by how you felt last night."),
        "Personal",
        List.of("WHAT TO DO", "WHY TODAY", "HOW TO DO IT", "IF IT DIDN'T WORK OUT"),
        "A NOTE FROM MOMNA",
        "We couldn't prepare your plan. Please try again."
    );

    private static final Set RU = new Set(
        new Choice("Персональный", "Ответьте на несколько вопросов — и рекомендации учтут, как вы себя чувствуете сегодня."),
        new Choice("Общий", "Общие рекомендации — без вопросов."),
        new Choice("Вчера вечером", "Ваш план — по тому, как вы чувствовали себя вчера вечером."),
        "Персональный",
        List.of("ЧТО СДЕЛАТЬ", "ПОЧЕМУ СЕГОДНЯ", "КАК СДЕЛАТЬ", "ЕСЛИ НЕ ПОЛУЧИЛОСЬ"),
        "ЗАМЕТКА ОТ MOMNA",
        "Не получилось подготовить план. Попробуйте ещё раз."
    );

    private static final Map<String, Set> BY_LANGUAGE = Map.of("en", EN, "ru", RU);

    public static Set of(String language) {
        return BY_LANGUAGE.getOrDefault(language, EN);
    }
}
