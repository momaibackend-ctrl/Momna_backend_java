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
        List<CardView> cards,
        ProgressView progress,
        ProvenanceView provenance
    ) {}

    public record CardView(
        String cardId,
        String slot,
        int position,
        String title,
        String microcopy,
        DetailsView details,
        String status,
        Instant statusChangedAt
    ) {}

    public record DetailsView(String whatToDo, String whyToday, String howToDo, String ifSkipped, String support) {}

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

    /** MOMNA-1027 entry state of the current local date; GET today never generates. */
    public record TodayView(String state, String localDate, String timezone, MyDayView myDay) {}
}
