package com.momna.modules.myday.application;

import com.momna.modules.myday.application.template.MyDayOverlayAdapter.HealthLevel;

/**
 * The code-owned lines that the backend adds to a day with a medical overlay (Product decision 25.11.2026).
 * The LLM writes the health card in the period's tone and may only suppose ("this can happen when...");
 * these lines are never generated or rephrased by it.
 *
 * <p>The recommendation follows the SafetyDecision level (MOMNA-989 section 7). PROMPT is the first sentence of
 * the canon's PROMPT text; its second sentence is about bleeding after menopause and is not general. URGENT and
 * EMERGENCY have no line here: the urgent path shows them at once, and My Day must not repeat them. The
 * disclaimer is Product's short wording of 25.11.2026.
 */
public final class MyDayHealthTexts {
    public static final String VERSION = "myday.health-texts.v1";

    private MyDayHealthTexts() {}

    public static String disclaimer(String language) {
        return ru(language)
            ? "Momna подсказывает, но не ставит диагноз и не заменяет врача."
            : "Momna offers guidance, not a diagnosis, and doesn't replace your doctor.";
    }

    /** The doctor recommendation of a level, or null when the urgent path already shows it. */
    public static String recommendation(HealthLevel level, String language) {
        if (level == null) return null;
        boolean ru = ru(language);
        return switch (level) {
            case PROMPT -> ru ? "Этот симптом стоит обсудить с медицинским специалистом."
                : "Please discuss this symptom with a healthcare professional.";
            case UNKNOWN -> ru
                ? "По указанным ответам нельзя уверенно оценить срочность. Если самочувствие ухудшается или есть признаки опасности, обратитесь за медицинской помощью."
                : "Your answers do not establish urgency reliably. Seek medical help if symptoms worsen or warning signs appear.";
            case URGENT, EMERGENCY -> null;
        };
    }

    private static boolean ru(String language) {
        return "ru".equals(language);
    }
}
