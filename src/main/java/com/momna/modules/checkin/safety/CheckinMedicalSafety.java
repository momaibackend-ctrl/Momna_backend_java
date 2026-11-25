package com.momna.modules.checkin.safety;

import com.momna.modules.checkin.application.CheckinException;
import com.momna.modules.checkin.application.CheckinViews.AnswerChange;
import com.momna.modules.checkin.domain.CheckinSafetyState;
import com.momna.modules.checkin.infrastructure.CheckinSessionEntity;
import com.momna.modules.checkin.scoring.CheckinScoringResult;
import com.momna.platform.safety.MedicalSafetyException;
import com.momna.platform.safety.MedicalSafetyFlowService;
import com.momna.platform.safety.MedicalStep;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Check-in safety for every lifecycle period on the v2.2 catalog (MOMNA-1145). The platform flow decides;
 * this class hands it the session's answers and projects the routes the session opened onto the session's
 * SafetyDecision, which My Day reads (MyDayOverlayAdapter, Product decision 25.11.2026).
 *
 * <p>The projection takes the strongest outcome of the session's routes. Its severity is the platform
 * severity of the disposition: EMERGENCY and the PM2 safety assessment are CRITICAL, URGENT is HIGH, PROMPT is
 * MEDIUM, UNKNOWN (an urgent sign answered "unsure") and a question still open are UNKNOWN, ROUTINE is LOW, and a
 * route answered with nothing found is NONE. Only MEDIUM and above, and UNKNOWN, give My Day a doctor line.
 */
@Service
public class CheckinMedicalSafety {
    public static final String ANSWER_SAFETY = "ANSWER_SAFETY";

    private final MedicalSafetyFlowService flow;

    public CheckinMedicalSafety(MedicalSafetyFlowService flow) {
        this.flow = flow;
    }

    /** The route list a session view shows. */
    public List<MedicalStep> steps(CheckinSessionEntity session) {
        return flow.steps(session.getUserId(), session.getSessionId());
    }

    public void onPatch(CheckinSessionEntity session, List<AnswerChange> changes, Map<String, Integer> answers,
        CheckinScoringResult score, Instant at) {
        var trigger = new MedicalSafetyFlowService.Trigger(
            session.getUserId(),
            session.getSessionId(),
            session.getPeriodAtTime(),
            changes.stream().map(c -> new MedicalSafetyFlowService.ItemAnswer(c.itemCode(), c.value())).toList(),
            answers,
            score.safetyThresholdReferenceReached(),
            score.contributions().stream()
                .map(c -> new MedicalSafetyFlowService.ScoreContribution(c.itemCode(), c.safetyContribution()))
                .toList());
        typed(() -> flow.onCheckinPatch(trigger));
        project(session, at);
    }

    public void answer(CheckinSessionEntity session, String routeId, String factCode, String value, Instant at) {
        typed(() -> flow.answer(session.getUserId(), session.getSessionId(), routeId, factCode, value));
        project(session, at);
    }

    public void revise(CheckinSessionEntity session, String routeId, String factCode, String value, Instant at) {
        typed(() -> flow.revise(session.getUserId(), session.getSessionId(), routeId, factCode, value));
        project(session, at);
    }

    public void checklist(CheckinSessionEntity session, String routeId, String mode, List<String> selected,
        Instant at) {
        typed(() -> flow.answerChecklist(session.getUserId(), session.getSessionId(), routeId, mode, selected));
        project(session, at);
    }

    public void bloodPressure(CheckinSessionEntity session, String routeId, Integer systolic, Integer diastolic,
        boolean unknown, Instant at) {
        typed(() -> flow.recordBloodPressure(session.getUserId(), session.getSessionId(), routeId,
            systolic, diastolic, unknown));
        project(session, at);
    }

    public void skip(CheckinSessionEntity session, String routeId, Instant at) {
        typed(() -> flow.skip(session.getUserId(), session.getSessionId(), routeId));
        project(session, at);
    }

    private void project(CheckinSessionEntity session, Instant at) {
        var own = flow.sessionSteps(session.getUserId(), session.getSessionId());
        var p = projection(own);
        session.applySafetyDecision(p.state(), p.decisionVersion(), p.routeCode(), p.severity(), p.blocking(),
            p.clarification(), p.allowedActions(), at);
    }

    /** The session's SafetyDecision from the routes it opened. Pure; visible for tests. */
    public static Projection projection(List<MedicalStep> own) {
        if (own.isEmpty()) {
            return new Projection(CheckinSafetyState.CLEAR, null, null, null, false, null, List.of());
        }
        boolean blocking = own.stream().anyMatch(MedicalStep::question);
        var top = own.stream()
            .max(Comparator.comparingInt((MedicalStep s) -> rank(severity(s)))
                .thenComparing(Comparator.comparingInt((MedicalStep s) -> own.indexOf(s)).reversed()))
            .orElseThrow();
        var clarification = new LinkedHashMap<String, Object>();
        clarification.put("catalogVersion", top.catalogVersion());
        clarification.put("routeIds", own.stream().map(MedicalStep::routeId).toList());
        clarification.put("disposition", top.disposition());
        clarification.put("screenCode", top.screenCode());
        return new Projection(
            blocking ? CheckinSafetyState.CLARIFICATION_REQUIRED : CheckinSafetyState.RESOLVED,
            top.catalogVersion() + ":" + top.ruleVersion(),
            top.routeCode(),
            severity(top),
            blocking,
            Map.copyOf(clarification),
            blocking ? List.of(ANSWER_SAFETY) : top.actionCode() == null ? List.of() : List.of(top.actionCode()));
    }

    /** The platform severity of a step (see the class comment). */
    public static String severity(MedicalStep step) {
        if (step.question()) return "UNKNOWN";
        return switch (step.disposition()) {
            case "EMERGENCY", "SAFETY_ASSESSMENT" -> "CRITICAL";
            case "URGENT" -> "HIGH";
            case "PROMPT" -> "MEDIUM";
            case "ROUTINE" -> "LOW";
            case "NOT_REPORTED" -> "NONE";
            default -> "UNKNOWN";
        };
    }

    private static int rank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 4;
            case "MEDIUM" -> 3;
            case "UNKNOWN" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static <T> T typed(Supplier<T> action) {
        try {
            return action.get();
        } catch (MedicalSafetyException refused) {
            throw new CheckinException(refused.code(), refused.getMessage());
        }
    }

    public record Projection(
        CheckinSafetyState state,
        String decisionVersion,
        String routeCode,
        String severity,
        boolean blocking,
        Map<String, Object> clarification,
        List<String> allowedActions
    ) {}
}
