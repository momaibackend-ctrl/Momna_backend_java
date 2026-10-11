package com.momna.modules.checkin.safety;

import com.momna.modules.checkin.application.CheckinException;
import com.momna.modules.checkin.domain.CheckinSafetyState;
import com.momna.modules.checkin.infrastructure.CheckinSessionEntity;
import com.momna.modules.checkin.scoring.CheckinScoringResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Check-in safety decision adapter. Deterministic version and clarification ID
 * follow the Kotlin StaticCheckinSafetyClarificationFlow contract.
 * The neutral response values are platform-policy defaults, not diagnostic advice.
 */
@Service
public class MenarcheSafetyDecisionService {
    private static final String RULE_VERSION = "safety.clinical.v1";
    private static final String CATALOG_VERSION = "safety.catalog.v1";
    private final MenarcheSafetyRouteSelector selector = new MenarcheSafetyRouteSelector();

    public void evaluate(CheckinSessionEntity session, CheckinScoringResult score, Instant at) {
        if (!"MENARCHE".equals(session.getPeriodAtTime())) return;
        if (session.isSafetyBlocking()) return;
        if (!score.safetyThresholdReferenceReached()) {
            session.applySafetyDecision(CheckinSafetyState.CLEAR, null, null, null,
                false, null, List.of(), at);
            return;
        }
        String route = selector.select(score).orElse("SAFETY_REVIEW_REQUIRED");
        String version = RULE_VERSION + ":" + CATALOG_VERSION + ":" + route;
        String id = fingerprint(session.getSessionId() + "|" + route + "|" + version);
        String labelKey = "checkin.safety." + route.toLowerCase(java.util.Locale.ROOT);
        var options = List.of(
            Map.of("value", "severe", "labelKey", labelKey + ".severe"),
            Map.of("value", "not_severe", "labelKey", labelKey + ".not_severe"),
            Map.of("value", "not_sure", "labelKey", labelKey + ".not_sure")
        );
        var clarification = Map.<String, Object>of(
            "id", id, "presentationKey", labelKey, "options", options
        );
        session.applySafetyDecision(CheckinSafetyState.CLARIFICATION_REQUIRED,
            version, route, "HIGH", true, clarification,
            List.of("DEFER_AI", "CONTACT_CLINICIAN"), at);
    }

    public void answer(CheckinSessionEntity session, String id, String value, Instant at) {
        var clarification = session.getSafetyClarification();
        if (!session.isSafetyBlocking() || clarification == null ||
            !Objects.equals(clarification.get("id"), id)) {
            throw new CheckinException("SAFETY_CLARIFICATION_NOT_CURRENT",
                "No current safety clarification matches the request");
        }
        // A critical decision cannot be downgraded through repeated submissions.
        if ("CRITICAL".equals(session.getSafetySeverity())) {
            throw new CheckinException("SAFETY_STATE_BLOCKING",
                "Emergency decision must not be downgraded");
        }
        switch (value) {
            case "severe" -> session.applySafetyDecision(
                CheckinSafetyState.CLARIFICATION_REQUIRED,
                session.getSafetyDecisionVersion(), session.getSafetyRouteCode(),
                "CRITICAL", true, clarification,
                List.of("CONTACT_EMERGENCY_SERVICES"), at);
            case "not_severe", "not_sure" -> session.applySafetyDecision(
                CheckinSafetyState.RESOLVED,
                session.getSafetyDecisionVersion(), session.getSafetyRouteCode(),
                "HIGH", false, null,
                List.of("DEFER_AI", "CONTACT_CLINICIAN"), at);
            default -> throw new CheckinException("SAFETY_VALUE_INVALID",
                "Unknown safety clarification option");
        }
    }

    private String fingerprint(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            var result = new StringBuilder(64);
            for (byte b : digest) result.append("%02x".formatted(b & 255));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
