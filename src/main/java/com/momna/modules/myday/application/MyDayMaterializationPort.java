package com.momna.modules.myday.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface MyDayMaterializationPort {
    Result upsert(Command command);

    /**
     * The My Day already stored for this user and local date, if any. One successful generation per
     * (user, local date) (MOMNA-989, 23.11.2026): once it exists, the morning Check-in is closed until
     * the evening and no later source changes the day.
     */
    java.util.Optional<String> findMyDayId(String userId, LocalDate localDate);

    record BaselineReference(
        String contextFingerprint,
        String projectionFingerprint,
        String modeCode,
        String bindingVersion,
        int contextPurposeVersion,
        int contextSchemaVersion,
        int outputSchemaVersion,
        BigDecimal share
    ) {}

    record SourceReference(
        String sourceType,
        String sourceRef,
        String phaseCode,
        String stateCode,
        String bindingRole,
        BigDecimal completionRatio,
        BigDecimal baseShare,
        BigDecimal effectiveShare,
        String definitionVersion,
        String rulesVersion
    ) {}

    record AdjustmentEvidence(
        String signalId,
        String signalCode,
        String sourceRef,
        String phaseCode,
        String tierCode,
        BigDecimal scoreContribution,
        BigDecimal completionRatio,
        BigDecimal effectivePhaseWeight,
        String rulesVersion,
        int definitionVersion
    ) {}

    record Command(
        String userId,
        LocalDate localDate,
        String timezone,
        String existingTargetMyDayId,
        String orchestrationId,
        String orchestrationVersion,
        String idempotencyKey,
        BaselineReference baseline,
        List<SourceReference> sources,
        List<AdjustmentEvidence> adjustments,
        /** Answered items of the final source sessions; they feed the overlay adapter only (MOMNA-1324). */
        List<com.momna.modules.myday.application.template.MyDayFacts.Observation> observations,
        /** The SafetyDecision of each final source session; medical overlays come only from here. */
        List<com.momna.modules.myday.application.template.MyDayFacts.SafetyProjection> safety
    ) {
        public Command {
            sources = List.copyOf(sources);
            adjustments = List.copyOf(adjustments);
            observations = observations == null ? List.of() : List.copyOf(observations);
            safety = safety == null ? List.of() : List.copyOf(safety);
        }
    }

    record Result(String myDayId, String outcome, String appliedOrchestrationId) {}
}
