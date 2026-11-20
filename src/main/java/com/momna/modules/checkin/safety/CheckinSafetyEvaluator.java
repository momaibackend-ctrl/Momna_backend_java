package com.momna.modules.checkin.safety;

import com.momna.modules.checkin.application.CheckinException;
import com.momna.modules.checkin.definition.CheckinSafetyCatalogException;
import com.momna.modules.checkin.definition.CheckinSafetyPlatformContribution;
import com.momna.modules.checkin.definition.CheckinSafetyRouteCatalog;
import com.momna.modules.checkin.domain.CheckinSafetyState;
import com.momna.modules.checkin.infrastructure.CheckinSessionEntity;
import com.momna.modules.checkin.scoring.CheckinScoringResult;
import com.momna.platform.safety.CanonicalSafetyEngine;
import com.momna.platform.safety.SafetyCatalogComposer;
import com.momna.platform.safety.SafetyDecision;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Check-in safety for every lifecycle period: threshold, route winner from the governed
 * catalog, then the platform safety decision over the composed catalog. Safety semantics
 * stay with the platform; this class only projects its decision onto the session.
 */
@Service
public class CheckinSafetyEvaluator {

    /** The safety state stored on a session. */
    public record Snapshot(
        CheckinSafetyState state,
        String decisionVersion,
        String routeCode,
        String severity,
        boolean blocking,
        Map<String, Object> clarification,
        List<String> allowedActions
    ) {
        public static Snapshot clear() {
            return new Snapshot(CheckinSafetyState.CLEAR, null, null, null, false, null, List.of());
        }

        public static Snapshot of(CheckinSessionEntity session) {
            return new Snapshot(session.getSafetyState(), session.getSafetyDecisionVersion(),
                session.getSafetyRouteCode(), session.getSafetySeverity(), session.isSafetyBlocking(),
                session.getSafetyClarification(), session.getSafetyAllowedActions());
        }

        String clarificationId() {
            if (clarification == null) return null;
            var id = clarification.get("clarificationId");
            return id instanceof String value ? value : null;
        }
    }

    private final CheckinSafetyRouteCatalog routes;
    private final Function<Set<String>, SafetyDecision> core;
    private final CheckinSafetyClarificationFlow clarifications;

    @Autowired
    public CheckinSafetyEvaluator(CheckinSafetyRouteCatalog routes) {
        this(routes, deterministic(platformEngine(routes)), CheckinSafetyClarificationFlow.standard(routes));
    }

    /** The core function is the platform engine; AI classification is never requested here. */
    public CheckinSafetyEvaluator(
        CheckinSafetyRouteCatalog routes,
        Function<Set<String>, SafetyDecision> core,
        CheckinSafetyClarificationFlow clarifications
    ) {
        this.routes = routes;
        this.core = core;
        this.clarifications = clarifications;
    }

    public static CanonicalSafetyEngine platformEngine(CheckinSafetyRouteCatalog routes) {
        var catalog = SafetyCatalogComposer.compose(
            List.of(CheckinSafetyPlatformContribution.contribution(routes)),
            routes.metadata().catalogVersion());
        return new CanonicalSafetyEngine(catalog);
    }

    private static Function<Set<String>, SafetyDecision> deterministic(CanonicalSafetyEngine engine) {
        return signals -> engine.evaluate(signals, false);
    }

    public void evaluate(CheckinSessionEntity session, CheckinScoringResult score, Instant at) {
        apply(session, evaluate(session.getSessionId(), Snapshot.of(session), score), at);
    }

    public void answer(CheckinSessionEntity session, String clarificationId, String value, Instant at) {
        apply(session, answer(session.getSessionId(), Snapshot.of(session), clarificationId, value), at);
    }

    /**
     * An open lock survives any answer change, even when the score drops below the threshold;
     * only a clarification answer can resolve it.
     */
    public Snapshot evaluate(String sessionId, Snapshot current, CheckinScoringResult score) {
        if (current.blocking()) return current;
        if (!score.safetyThresholdReferenceReached()) return Snapshot.clear();
        var selection = select(score);
        var decision = core.apply(Set.of(selection.itemCode()));
        if (!decision.route().equals(selection.route().code())) {
            throw new CheckinException("SAFETY_ROUTE_VERSION_MISMATCH", "Safety decision does not match the selected route");
        }
        return project(sessionId, decision, selection.route().contentRef());
    }

    public Snapshot answer(String sessionId, Snapshot current, String clarificationId, String value) {
        if (current.routeCode() == null || current.decisionVersion() == null || current.clarification() == null
            || !current.blocking() || !clarificationId.equals(current.clarificationId())) {
            throw new CheckinException("SAFETY_CLARIFICATION_NOT_CURRENT",
                "No current safety clarification matches the request");
        }
        var signals = clarifications.answer(sessionId, current.routeCode(), current.decisionVersion(),
            clarificationId, value);
        var decision = core.apply(signals);
        String contentRef = null;
        if (decision.allowedActions().contains(CheckinSafetyRouteCatalog.OPEN_SAFETY_CLARIFICATION)) {
            try {
                contentRef = routes.route(decision.route()).contentRef();
            } catch (CheckinSafetyCatalogException failure) {
                throw new CheckinException("SAFETY_ROUTE_VERSION_MISMATCH", "Safety route is not in the active catalog");
            }
        }
        return project(sessionId, decision, contentRef);
    }

    private CheckinSafetyRouteCatalog.Selection select(CheckinScoringResult score) {
        var bound = routes.bindings().stream()
            .map(CheckinSafetyRouteCatalog.Binding::itemCode)
            .collect(Collectors.toSet());
        var contributions = score.contributions().stream()
            .filter(c -> c.safetyContribution().signum() > 0 && bound.contains(c.itemCode()))
            .map(c -> new CheckinSafetyRouteCatalog.ItemContribution(c.itemCode(), c.safetyContribution()))
            .toList();
        try {
            return routes.selectRoute(contributions, routes.metadata().catalogVersion());
        } catch (CheckinSafetyCatalogException failure) {
            if (failure.code() == CheckinSafetyCatalogException.Code.VERSION_NOT_FOUND) {
                throw new CheckinException("SAFETY_ROUTE_VERSION_MISMATCH", "Safety catalog version is unavailable");
            }
            throw new CheckinException("DEPENDENCY_UNAVAILABLE", "Safety route selection is unavailable");
        }
    }

    private Snapshot project(String sessionId, SafetyDecision decision, String contentRef) {
        var decisionVersion = decision.catalogVersion() + ":" + decision.ruleVersion();
        boolean requiresClarification =
            decision.allowedActions().contains(CheckinSafetyRouteCatalog.OPEN_SAFETY_CLARIFICATION);
        var clarification = requiresClarification
            ? clarifications.open(sessionId, decision.route(), decisionVersion, contentRef)
            : null;
        return new Snapshot(
            requiresClarification ? CheckinSafetyState.CLARIFICATION_REQUIRED : CheckinSafetyState.RESOLVED,
            decisionVersion,
            decision.route(),
            decision.severity().name(),
            decision.blocked() || requiresClarification,
            clarification,
            List.copyOf(decision.allowedActions())
        );
    }

    private static void apply(CheckinSessionEntity session, Snapshot snapshot, Instant at) {
        session.applySafetyDecision(snapshot.state(), snapshot.decisionVersion(), snapshot.routeCode(),
            snapshot.severity(), snapshot.blocking(), snapshot.clarification(), snapshot.allowedActions(), at);
    }
}
