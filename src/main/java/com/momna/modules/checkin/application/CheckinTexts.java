package com.momna.modules.checkin.application;

import com.momna.modules.checkin.domain.CheckinPhase;
import java.util.Locale;

/**
 * Server-owned Check-in texts (app decision D-2). The Russian morning-closed text is Product's
 * wording of 23.11.2026; the English one is a draft awaiting Product approval (MOMNA-989 section 3).
 * The Check-in tab texts follow the implemented design (Figma, Adult Cycle frames C1 929:79844,
 * C2 929:79857, evening 1152:72244 and 1152:72279, export of 24.11.2026); Russian is a draft.
 */
public final class CheckinTexts {
    public static final String MORNING_PLAN_ALREADY_SET = "MY_DAY_PLAN_ALREADY_SET";

    private CheckinTexts() {}

    /** The card of a phase on the Check-in tab; title may be null (the evening "completed" card has none). */
    public record PhaseCard(String title, String body) {}

    public static String morningPlanAlreadySet(String preferredLanguage) {
        return ru(preferredLanguage)
            ? "План на сегодня уже составлен. Вы можете пройти чек-ин сегодня вечером и каждый день утром до составления плана дня."
            : "Today's plan is already set. You can check in this evening, and every morning before your plan is made.";
    }

    /** The Check-in tab subtitle under its header. */
    public static String subtitle(String preferredLanguage) {
        return ru(preferredLanguage) ? "То, как вы себя чувствуете, превращается в план." : "How you feel, turned into a plan.";
    }

    /** The invitation shown while the phase can be started or resumed. */
    public static PhaseCard invite(CheckinPhase phase, String preferredLanguage) {
        boolean ru = ru(preferredLanguage);
        return phase == CheckinPhase.MORNING
            ? (ru ? new PhaseCard("Как вы сегодня?",
                    "Несколько коротких вопросов о настроении, энергии и теле — и я выстрою ваш день по ответам.")
                : new PhaseCard("How are you today?",
                    "A few quick questions about mood, energy, and body — I'll shape your day around the answers."))
            : (ru ? new PhaseCard("Как прошёл день?", "Отметьте, как прошёл день, — от этого зависит план на завтра.")
                : new PhaseCard("How did today go?", "Note how the day went — it shapes tomorrow's plan."));
    }

    /** The card shown once the phase is final. */
    public static PhaseCard completed(CheckinPhase phase, String preferredLanguage) {
        boolean ru = ru(preferredLanguage);
        return phase == CheckinPhase.MORNING
            ? (ru ? new PhaseCard("Ваш персональный план готов", "Сегодня всё подобрано под ваше самочувствие.")
                : new PhaseCard("Your personal plan is ready", "Today is tailored to how you feel."))
            : (ru ? new PhaseCard(null, "План на завтра уже готовится — увидимся утром.")
                : new PhaseCard(null, "Your plan for tomorrow is on its way — see you in the morning."));
    }

    private static boolean ru(String preferredLanguage) {
        return preferredLanguage != null && preferredLanguage.toLowerCase(Locale.ROOT).startsWith("ru");
    }
}
