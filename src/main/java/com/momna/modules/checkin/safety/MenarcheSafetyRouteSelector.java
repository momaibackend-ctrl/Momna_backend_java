package com.momna.modules.checkin.safety;

import com.momna.modules.checkin.scoring.CheckinScoringResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical Menarche bindings from checkin-safety-route-catalog.json.
 * Select by descending positive contribution, then definition display order.
 * This class selects a route; it does not make a clinical disposition.
 */
public final class MenarcheSafetyRouteSelector {
    private static final Map<String, String> ROUTES = Map.of(
        "M3", "MENARCHE_HEADACHE",
        "M4", "MENARCHE_DIZZINESS",
        "M5", "MENARCHE_BLEEDING"
    );

    public Optional<String> select(CheckinScoringResult result) {
        if (!result.safetyThresholdReferenceReached()) return Optional.empty();
        var contributions = result.contributions();
        return contributions.stream()
            .filter(c -> ROUTES.containsKey(c.itemCode()))
            .filter(c -> c.safetyContribution().compareTo(BigDecimal.ZERO) > 0)
            .sorted((a, b) -> {
                int byRisk = b.safetyContribution().compareTo(a.safetyContribution());
                if (byRisk != 0) return byRisk;
                return Integer.compare(index(contributions, a.itemCode()),
                    index(contributions, b.itemCode()));
            })
            .map(c -> ROUTES.get(c.itemCode()))
            .findFirst();
    }

    private int index(java.util.List<CheckinScoringResult.Contribution> items, String code) {
        for (int i = 0; i < items.size(); i++)
            if (items.get(i).itemCode().equals(code)) return i;
        return Integer.MAX_VALUE;
    }
}
