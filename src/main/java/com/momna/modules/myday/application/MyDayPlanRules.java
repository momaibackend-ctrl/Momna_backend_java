package com.momna.modules.myday.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Product rules of My Day, decided 23.11.2026 (MOMNA-989 sections 2-4, MOMNA-106 item 2).
 *
 * <p>A plan is PERSONAL when at least one final Check-in took part in it: Morning(D) or Evening(D-1).
 * It is STANDARD only when neither did. The kind follows the sources of the day, never the button the
 * user pressed, and the second plan-choice button is chosen by the same rule, so its label always
 * matches the plan it produces.
 */
public final class MyDayPlanRules {
    public static final String PERSONAL = "PERSONAL";
    public static final String STANDARD = "STANDARD";
    /** Second choice on screen A when no evening Check-in feeds today: "Get General Plan". */
    public static final String SECONDARY_GENERAL = "GENERAL";
    /** Second choice on screen A when Evening(D-1) feeds today: "Get My Plan". */
    public static final String SECONDARY_FROM_EVENING = "FROM_EVENING_CHECKIN";

    public static final String PENDING = "PENDING";
    public static final String COMPLETED = "COMPLETED";
    public static final String SKIPPED = "SKIPPED";
    /** States a card may be set to. SKIPPED is stored when sent, but the app never sends it. */
    public static final Set<String> CARD_STATES = Set.of(PENDING, COMPLETED, SKIPPED);
    /** MyDayCompleted counts these slots only; optional cards never block the day. */
    public static final Set<String> COMPLETION_SLOTS = Set.of("AM", "PM");

    private MyDayPlanRules() {}

    /** A source counts only when it actually contributes: a final Check-in with no answers does not. */
    public static boolean contributes(BigDecimal effectiveShare) {
        return effectiveShare != null && effectiveShare.signum() > 0;
    }

    public static String kind(Collection<BigDecimal> checkinEffectiveShares) {
        return checkinEffectiveShares.stream().anyMatch(MyDayPlanRules::contributes) ? PERSONAL : STANDARD;
    }

    /**
     * Before any plan exists, a morning Check-in can only be added, so the plan made without it is
     * personal exactly when Evening(D-1) contributes.
     */
    public static String secondaryChoice(boolean eveningContributes) {
        return eveningContributes ? SECONDARY_FROM_EVENING : SECONDARY_GENERAL;
    }

    /**
     * Card progress toggles both ways (design D-7, "Mark as Not Completed"). Setting the state a card
     * already has is a conflict, as before, so a stale double tap is reported instead of hidden.
     */
    public static boolean allowedTransition(String from, String to) {
        return CARD_STATES.contains(from) && CARD_STATES.contains(to) && !from.equals(to);
    }

    /** The day is complete when every AM and PM card is COMPLETED. */
    public static boolean dayComplete(Map<String, String> statusBySlotCard, Map<String, String> slotByCard) {
        var counted = slotByCard.entrySet().stream()
            .filter(e -> COMPLETION_SLOTS.contains(e.getValue()))
            .map(Map.Entry::getKey)
            .toList();
        return !counted.isEmpty() && counted.stream().allMatch(card -> COMPLETED.equals(statusBySlotCard.get(card)));
    }
}
