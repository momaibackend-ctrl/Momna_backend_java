package com.momna.modules.checkin.application;

import java.util.Locale;

/**
 * Server-owned Check-in texts (app decision D-2). The Russian morning-closed text is Product's
 * wording of 23.11.2026; the English one is a draft awaiting Product approval (MOMNA-989 section 3).
 */
public final class CheckinTexts {
    public static final String MORNING_PLAN_ALREADY_SET = "MY_DAY_PLAN_ALREADY_SET";

    private CheckinTexts() {}

    public static String morningPlanAlreadySet(String preferredLanguage) {
        return preferredLanguage != null && preferredLanguage.toLowerCase(Locale.ROOT).startsWith("ru")
            ? "План на сегодня уже составлен. Вы можете пройти чек-ин сегодня вечером и каждый день утром до составления плана дня."
            : "Today's plan is already set. You can check in this evening, and every morning before your plan is made.";
    }
}
