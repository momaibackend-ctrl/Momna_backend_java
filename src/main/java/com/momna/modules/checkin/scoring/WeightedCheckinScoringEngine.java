package com.momna.modules.checkin.scoring;

import com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog;
import com.momna.modules.checkin.domain.CheckinDefinitionPeriod;
import com.momna.modules.checkin.domain.CheckinPhase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import org.springframework.stereotype.Service;

@Service
public class WeightedCheckinScoringEngine {
    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ADJUSTMENT_MILD = new BigDecimal("4");
    private static final BigDecimal ADJUSTMENT_MODERATE = new BigDecimal("10");
    private static final BigDecimal ADJUSTMENT_STRONG = new BigDecimal("18");
    private static final BigDecimal SAFETY_REFERENCE_THRESHOLD = new BigDecimal("12");

    private final WeightedCheckinDefinitionCatalog definitions;

    public WeightedCheckinScoringEngine(WeightedCheckinDefinitionCatalog definitions) {
        this.definitions = definitions;
    }

    public CheckinScoringResult score(
        CheckinDefinitionPeriod period,
        CheckinPhase phase,
        int definitionVersion,
        Set<String> applicableItemCodes,
        Map<String, Integer> answers,
        Map<String, String> profileModifiers
    ) {
        if (!applicableItemCodes.containsAll(answers.keySet())) {
            throw new IllegalArgumentException("Answer contains non-applicable check-in item");
        }
        if (answers.values().stream().anyMatch(value -> value == null || value < 0 || value > 2)) {
            throw new IllegalArgumentException("Check-in answer values must be 0, 1 or 2");
        }

        var definition = definitions.get(period, phase, definitionVersion);
        var contributions = new ArrayList<CheckinScoringResult.Contribution>();

        for (var item : definition.items()) {
            if (!applicableItemCodes.contains(item.itemCode())) continue;
            var state = answers.get(item.itemCode());
            if (state == null || !item.scored()) continue;

            var adjustment = effectiveWeight(
                item.adjustmentWeight(), item.profileMultipliers(), "adj_w", profileModifiers
            );
            var safety = effectiveWeight(
                item.safetyWeight(), item.profileMultipliers(), "safety_w", profileModifiers
            );
            var applied = item.profileMultipliers().stream()
                .filter(rule -> Objects.equals(profileModifiers.get(rule.profileField()), rule.value()))
                .map(WeightedCheckinDefinitionCatalog.Multiplier::ruleId)
                .distinct()
                .sorted()
                .toList();

            contributions.add(new CheckinScoringResult.Contribution(
                item.itemCode(),
                state,
                decimal(item.adjustmentWeight()),
                adjustment,
                normalize(adjustment.multiply(BigDecimal.valueOf(state))),
                decimal(item.safetyWeight()),
                safety,
                normalize(safety.multiply(BigDecimal.valueOf(state))),
                applied
            ));
        }

        var adjustmentScore = sum(contributions.stream()
            .map(CheckinScoringResult.Contribution::adjustmentContribution).toList());
        var safetyScore = sum(contributions.stream()
            .map(CheckinScoringResult.Contribution::safetyContribution).toList());

        var tier = adjustmentScore.compareTo(ADJUSTMENT_STRONG) >= 0
            ? CheckinAdjustmentTier.STRONG
            : adjustmentScore.compareTo(ADJUSTMENT_MODERATE) >= 0
                ? CheckinAdjustmentTier.MODERATE
                : adjustmentScore.compareTo(ADJUSTMENT_MILD) >= 0
                    ? CheckinAdjustmentTier.MILD
                    : CheckinAdjustmentTier.NONE;

        return new CheckinScoringResult(
            adjustmentScore,
            safetyScore,
            tier,
            safetyScore.compareTo(SAFETY_REFERENCE_THRESHOLD) >= 0,
            answers.size(),
            contributions.size(),
            List.copyOf(contributions)
        );
    }

    private BigDecimal effectiveWeight(
        double base,
        List<WeightedCheckinDefinitionCatalog.Multiplier> rules,
        String target,
        Map<String, String> modifiers
    ) {
        var value = BigDecimal.valueOf(base);
        for (var rule : rules.stream()
            .filter(rule -> target.equals(rule.targetWeight()))
            .sorted(Comparator.comparing(WeightedCheckinDefinitionCatalog.Multiplier::ruleId))
            .toList()) {
            if (Objects.equals(modifiers.get(rule.profileField()), rule.value())) {
                value = value.multiply(BigDecimal.valueOf(rule.multiplier()));
            }
        }
        return normalize(value);
    }

    private BigDecimal decimal(double value) {
        return normalize(BigDecimal.valueOf(value));
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        var total = BigDecimal.ZERO;
        for (var value : values) total = total.add(value);
        return normalize(total);
    }
}
