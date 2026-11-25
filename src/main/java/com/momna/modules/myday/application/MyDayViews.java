package com.momna.modules.myday.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MyDayViews {
    private MyDayViews() {}

    public record MyDayView(
        String myDayId,
        String localDate,
        String timezoneAtGeneration,
        String status,
        String lifecyclePeriod,
        String language,
        long revision,
        Instant generatedAt,
        Instant updatedAt,
        /** PERSONAL when Morning(D) or Evening(D-1) took part, otherwise STANDARD (MOMNA-989 section 2). */
        String kind,
        /** Header badge text, e.g. "Personal"; null shows no badge. */
        String badge,
        List<CardView> cards,
        ProgressView progress,
        ProvenanceView provenance,
        /** Present only on a day with a medical overlay; null on every other day (no doctor advice then). */
        HealthNoteView healthNote,
        /** The header of the plan block ("TODAY'S PLAN"). */
        String planHeader,
        /** The plan by part of the day, in order AM, PM, OPTIONAL: the app's TaskGroup. Empty slots are left out. */
        List<GroupView> groups,
        /** Shown when no group has a card. */
        String emptyPlanText,
        /**
         * The "Make it personal" offer; always null. One generation per day (MOMNA-989, 23.11.2026): once a plan
         * exists the morning Check-in is closed until the evening, so a standard plan cannot become personal today.
         */
        ChoiceView upgradeOffer
    ) {}

    /** One part of the day: MORNING, EVENING or OPTIONAL, its label and its cards in order. */
    public record GroupView(String part, String title, List<String> cardIds) {}

    /**
     * Code-owned lines for the day's health card (Product decision 25.11.2026). level is PROMPT, URGENT,
     * EMERGENCY or UNKNOWN; recommendation is null for URGENT and EMERGENCY, which the urgent safety path
     * already shows; the disclaimer is always present.
     */
    public record HealthNoteView(String level, String recommendation, String disclaimer) {}

    public record CardView(
        String cardId,
        String slot,
        int position,
        String title,
        String microcopy,
        DetailsView details,
        /** The details sheet as designed: WHAT TO DO, WHY TODAY, HOW TO DO IT, IF IT DIDN'T WORK OUT. */
        List<SectionView> sections,
        /** "A NOTE FROM MOMNA"; body is the card's support text. */
        NoteView note,
        String status,
        Instant statusChangedAt
    ) {}

    public record DetailsView(String whatToDo, String whyToday, String howToDo, String ifSkipped, String support) {}

    public record SectionView(String header, String text) {}

    /** title stays null until the prompt produces one (myday.prompts.v1.2); the app shows no title then. */
    public record NoteView(String badge, String title, String body) {}

    public record ProgressView(int total, int completed, int skipped, int pending) {}

    /** Allowed provenance only: no session ids, raw answers, signals or safety payloads. */
    public record ProvenanceView(
        String targetLocalDate,
        BigDecimal baselineShare,
        List<SourceView> sources,
        String cardSource,
        String generationPolicyVersion,
        String promptVersion,
        String modelRef
    ) {}

    public record SourceView(String phase, String sourceLocalDate, String state, String completionRatio, String effectiveShare) {}

    public record GenerateResult(MyDayView myDay, String outcome) {}

    public record ChoiceView(String title, String description) {}

    /**
     * Screen A before the plan. secondary is GENERAL ("Get General Plan", a standard plan) or
     * FROM_EVENING_CHECKIN ("Get My Plan", a personal plan from Evening(D-1)); both call generate with
     * source STANDARD, and the plan's kind always matches the secondary shown.
     */
    public record PlanChoiceView(String secondary, ChoiceView personal, ChoiceView standard) {}

    /**
     * MOMNA-1027 entry state of the current local date. GET today generates in one case only: the
     * morning window has closed and there is no plan (MOMNA-989, 23.11.2026). planChoice and
     * generationFailure are present only while there is no plan.
     */
    public record TodayView(
        String state,
        String localDate,
        String timezone,
        MyDayView myDay,
        PlanChoiceView planChoice,
        String generationFailure,
        /** The My Day header subtitle of the period ("Recommendations for a regular cycle."); null when not designed yet. */
        String subtitle,
        /** Screen L texts while the plan is being made ("Getting your plan ready"). */
        ChoiceView generating
    ) {}
}
