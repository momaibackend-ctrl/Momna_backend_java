package com.momna.modules.checkin.application;

import com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog;
import com.momna.modules.checkin.domain.*;
import com.momna.modules.checkin.infrastructure.CheckinSessionEntity;
import com.momna.modules.checkin.scoring.CheckinScoringResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Deterministic Check-in risk routing. Uses the canonical weighted model
 * for item-to-route bindings and the platform safety contract's clinical route.
 *
 * The source archive does NOT contain clinical clarification choices. Therefore
 * answering a genuine clarification remains fail-closed until a reviewed
 * option-to-signal catalog is provided; never infer clinical clearance.
 */
@Service
public class CheckinSafetyRoutingService {
    private final WeightedCheckinDefinitionCatalog definitions;
    public CheckinSafetyRoutingService(WeightedCheckinDefinitionCatalog definitions) {
        this.definitions = definitions;
    }

    public void evaluate(
        CheckinSessionEntity session,
        CheckinScoringResult scoring,
        Instant now
    ) {
        if (session.isSafetyBlocking()) return;
        if (!scoring.safetyThresholdReferenceReached()) {
            session.applySafety(CheckinSafetyState.CLEAR, null, null, null,
                false, null, List.of(), now);
            return;
        }

        var definition = definitions.get(
            CheckinDefinitionPeriod.valueOf(session.getPeriodAtTime()),
            session.getPhase(), session.getDefinitionVersion()
        );
        var orders = new HashMap<String, Integer>();
        for (var item : definition.items()) orders.put(item.itemCode(), item.displayOrder());
        var candidate = scoring.contributions().stream()
            .filter(x -> x.safetyContribution().signum() > 0)
            .filter(x -> definitions.safetyRouteForItem(x.itemCode()) != null)
            .sorted(Comparator
                .comparing(CheckinScoringResult.Contribution::safetyContribution)
                .reversed()
                .thenComparingInt(x -> orders.getOrDefault(x.itemCode(), Integer.MAX_VALUE))
                .thenComparing(CheckinScoringResult.Contribution::itemCode))
            .findFirst()
            .orElse(null);
        // The canonical model requires fail-closed routing on unknown bindings.
        var route = candidate == null
            ? "SAFETY_REVIEW_REQUIRED"
            : definitions.safetyRouteForItem(candidate.itemCode());
        var decisionVersion = "safety.clinical.v1:safety.catalog.v1:" + route;
        var clarificationId = sha(session.getSessionId() + "|" + route + "|" + decisionVersion);
        var clarification = new LinkedHashMap<String, Object>();
        clarification.put("clarificationId", clarificationId);
        clarification.put("presentationKey", "checkin.safety." + route.toLowerCase(Locale.ROOT));
        // No unreviewed clinical choices are emitted to the user.
        clarification.put("options", List.of());
        session.applySafety(
            CheckinSafetyState.CLARIFICATION_REQUIRED,
            decisionVersion, route, "HIGH", true,
            clarification, List.of("DEFER_AI", "CONTACT_CLINICIAN"), now
        );
    }

    public void validateClarification(
        CheckinSessionEntity session, String clarificationId, String value
    ) {
        if (session.getSafetyState() != CheckinSafetyState.CLARIFICATION_REQUIRED
            || !session.isSafetyBlocking()
            || session.getSafetyDecisionVersion() == null
            || session.getSafetyRouteCode() == null
            || session.getSafetyClarification() == null
            || !Objects.equals(session.getSafetyClarification().get("clarificationId"), clarificationId)) {
            throw new CheckinException("SAFETY_CLARIFICATION_NOT_CURRENT",
                "The clarification is not current");
        }
        if (value == null || value.isBlank()) {
            throw new CheckinException("SAFETY_VALUE_INVALID",
                "Clarification value is required");
        }
        // A clinical choice-to-signal mapping is not present in the source model.
        // Never mark an unresolved clinical risk as RESOLVED by guessing.
        throw new CheckinException("DEPENDENCY_UNAVAILABLE",
            "Reviewed clinical clarification definitions are unavailable");
    }

    private String sha(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            var result = new StringBuilder();
            for (byte b : bytes) result.append("%02x".formatted(b & 0xff));
            return result.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
