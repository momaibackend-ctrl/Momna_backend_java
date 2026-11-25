package com.momna.modules.checkin.application;

import com.momna.modules.checkin.domain.CheckinPhase;
import java.util.Locale;

/**
 * Server-owned Check-in texts (app decision D-2). The Russian morning-closed text is Product's
 * wording of 23.11.2026; the English one and every Russian text below were approved by Product on 26.11.2026.
 * The Check-in tab texts follow the implemented design (Figma, Adult Cycle frames C1 929:79844,
 * C2 929:79857, evening 1152:72244 and 1152:72279, export of 24.11.2026).
 */
public final class CheckinTexts {
    public static final String MORNING_PLAN_ALREADY_SET = "MY_DAY_PLAN_ALREADY_SET";

    private CheckinTexts() {}

    /**
     * The fewest answers that let the user apply a Check-in (the app's minAnswersToApply): answering every item
     * is never required, one answer is enough to submit.
     */
    public static final int MIN_ANSWERS_TO_APPLY = 1;

    /**
     * The card of a phase on the Check-in tab; title may be null (the evening "completed" card has none).
     * statusLabel is the "Completed" label of a completed card, null on the invitation.
     */
    public record PhaseCard(String title, String body, String statusLabel) {
        public PhaseCard(String title, String body) {
            this(title, body, null);
        }
    }

    /** The Check-in sheet title and subtitle of a phase ("Morning Check-in"). */
    public record Sheet(String title, String subtitle) {}

    /**
     * The sheet of a phase. The morning texts follow the implemented design (Q, 929:79623); the evening ones and
     * the Russian texts were written by the backend and approved by Product on 26.11.2026.
     */
    public static Sheet sheet(CheckinPhase phase, String preferredLanguage) {
        boolean ru = ru(preferredLanguage);
        return phase == CheckinPhase.MORNING
            ? (ru ? new Sheet("Утренний чек-ин", "Расскажите, как вы себя чувствуете, — остальное я возьму на себя.")
                : new Sheet("Morning Check-in", "Tell me how you feel — I'll handle today."))
            : (ru ? new Sheet("Вечерний чек-ин", "Расскажите, как прошёл день, — я учту это завтра.")
                : new Sheet("Evening Check-in", "Tell me how today went — I'll tune tomorrow."));
    }

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
            ? (ru ? new PhaseCard("Ваш персональный план готов", "Сегодня всё подобрано под ваше самочувствие.", "Пройден")
                : new PhaseCard("Your personal plan is ready", "Today is tailored to how you feel.", "Completed"))
            : (ru ? new PhaseCard(null, "План на завтра уже готовится — увидимся утром.", "Пройден")
                : new PhaseCard(null, "Your plan for tomorrow is on its way — see you in the morning.", "Completed"));
    }

    private static boolean ru(String preferredLanguage) {
        return preferredLanguage != null && preferredLanguage.toLowerCase(Locale.ROOT).startsWith("ru");
    }
}
