package com.momna.modules.checkin.scoring;

import java.math.BigDecimal;
import java.util.List;

public record CheckinScoringResult(
    BigDecimal adjustmentScore,
    BigDecimal safetyScore,
    CheckinAdjustmentTier adjustmentTier,
    boolean safetyThresholdReferenceReached,
    int answeredItemCount,
    int scoredAnsweredItemCount,
    List<Contribution> contributions
) {
    public record Contribution(
        String itemCode,
        int statePoints,
        BigDecimal rawAdjustmentWeight,
        BigDecimal effectiveAdjustmentWeight,
        BigDecimal adjustmentContribution,
        BigDecimal rawSafetyWeight,
        BigDecimal effectiveSafetyWeight,
        BigDecimal safetyContribution,
        List<String> appliedMultiplierRuleIds
    ) {}
}
