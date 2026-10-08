package com.momna.modules.myday.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface MyDayMaterializationPort {
    Result upsert(Command command);

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
        List<AdjustmentEvidence> adjustments
    ) {}

    record Result(String myDayId, String outcome, String appliedOrchestrationId) {}
}
