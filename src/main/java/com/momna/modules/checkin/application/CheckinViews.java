package com.momna.modules.checkin.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class CheckinViews {
    private CheckinViews() {}

    public record AnswerChange(String itemCode, Integer value) {}
    public record StartResult(SessionView session, boolean created) {}
    public record FinalizeResult(SessionView session, DownstreamView downstream) {}
    public record DownstreamView(String myDayStatus, String myDayId, String nextAction) {}

    public record TodayView(
        String localDate,
        String timezone,
        String lifecyclePeriod,
        String lifecycleSubstage,
        List<PhaseSummary> phases
    ) {}

    public record PhaseSummary(
        String phase,
        String windowState,
        Instant opensAt,
        Instant closesAt,
        String sessionId,
        String sessionStatus,
        Instant consumedAt,
        String consumedByMyDayId,
        String allowedEntryAction
    ) {}

    public record SessionView(
        String sessionId,
        String phase,
        String localDate,
        String timezoneAtSession,
        String lifecyclePeriodAtTime,
        String lifecycleSubstageAtTime,
        String status,
        long revision,
        String definitionVersion,
        String rulesVersion,
        List<ItemView> items,
        List<AnswerView> answers,
        double completionRatio,
        WindowView window,
        SafetyView safety,
        Instant finalizedAt,
        String finalizationReason,
        Instant consumedAt,
        String consumedByMyDayId,
        Set<String> allowedActions
    ) {}

    public record ItemView(
        String itemCode,
        int order,
        String group,
        String labelKey,
        boolean applicable,
        Set<Integer> allowedValues
    ) {}

    public record AnswerView(String itemCode, int value) {}
    public record WindowView(String state, Instant opensAt, Instant closesAt) {}
    public record SafetyView(String state, boolean blocking) {}
}
