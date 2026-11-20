package com.momna.modules.checkin.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.momna.modules.checkin.application.CheckinException;
import com.momna.modules.checkin.definition.CheckinSafetyRouteCatalog;
import com.momna.modules.checkin.domain.CheckinSafetyState;
import com.momna.modules.checkin.safety.CheckinSafetyEvaluator.Snapshot;
import com.momna.modules.checkin.scoring.CheckinAdjustmentTier;
import com.momna.modules.checkin.scoring.CheckinScoringResult;
import com.momna.platform.safety.SafetyDecision;
import com.momna.platform.safety.SafetyDisposition;
import com.momna.platform.safety.SafetySeverity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * Ported from the working Kotlin WeightedCheckinSessionEvaluatorTest with the same vectors,
 * plus the same flow through the real composed platform engine for every bound item.
 */
class CheckinSafetyEvaluatorTest {
    private static final BigDecimal THRESHOLD = new BigDecimal("12");
    private static final String SESSION = "session-1";
    private final CheckinSafetyRouteCatalog routeCatalog = new CheckinSafetyRouteCatalog();
    private final CheckinSafetyClarificationFlow flow = CheckinSafetyClarificationFlow.standard(routeCatalog);

    @Test
    void belowThresholdIsClearAndDoesNotCallCoreSafety() {
        var safety = new RecordingSafety(routeCatalog);
        var result = evaluator(safety).evaluate(SESSION, Snapshot.clear(), score("11.999999", List.of()));
        assertEquals(CheckinSafetyState.CLEAR, result.state());
        assertFalse(result.blocking());
        assertTrue(safety.calls.isEmpty());
    }

    @Test
    void exactThresholdTriggersRouteAndBlockingDeterministicClarification() {
        var bindings = routeCatalog.bindings().subList(0, 2);
        var contributions = List.of(contribution(bindings.get(0).itemCode(), "7.000000"),
            contribution(bindings.get(1).itemCode(), "9.000000"));
        var safety = new RecordingSafety(routeCatalog);
        var result = evaluator(safety).evaluate(SESSION, Snapshot.clear(), score("12.000000", contributions));
        assertEquals(bindings.get(1).routeCode(), result.routeCode());
        assertEquals(CheckinSafetyState.CLARIFICATION_REQUIRED, result.state());
        assertTrue(result.blocking());
        assertNotNull(result.clarification());
        assertEquals(List.of(Set.of(bindings.get(1).itemCode())), safety.calls);
    }

    @Test
    void aboveThresholdAlsoTriggers() {
        var binding = routeCatalog.bindings().get(0);
        var result = evaluator(new RecordingSafety(routeCatalog)).evaluate(SESSION, Snapshot.clear(),
            score("12.000001", List.of(contribution(binding.itemCode(), "12.000001"))));
        assertTrue(result.blocking());
        assertEquals(binding.routeCode(), result.routeCode());
    }

    @Test
    void thresholdWithoutPositiveBoundContributionFailsClosed() {
        var safety = new RecordingSafety(routeCatalog);
        var failure = assertThrows(CheckinException.class,
            () -> evaluator(safety).evaluate(SESSION, Snapshot.clear(), score("12.000000", List.of())));
        assertEquals("DEPENDENCY_UNAVAILABLE", failure.code());
        assertTrue(safety.calls.isEmpty());
    }

    @Test
    void openLockSurvivesAnswerDeletionAndScoreFallingBelowThreshold() {
        var route = routeCatalog.bindings().get(0);
        var safety = new RecordingSafety(routeCatalog);
        var triggered = evaluator(safety).evaluate(SESSION, Snapshot.clear(),
            score("12.000000", List.of(contribution(route.itemCode(), "12.000000"))));
        safety.calls.clear();
        var below = evaluator(safety).evaluate(SESSION, triggered, score("0.000000", List.of()));
        assertEquals(triggered, below);
        assertTrue(below.blocking());
        assertTrue(safety.calls.isEmpty());
    }

    @Test
    void clarificationSafeOptionResolvesLockOnlyThroughCoreSafety() {
        var binding = routeCatalog.bindings().get(0);
        var safety = new RecordingSafety(routeCatalog);
        var triggered = evaluator(safety).evaluate(SESSION, Snapshot.clear(),
            score("12.000000", List.of(contribution(binding.itemCode(), "12.000000"))));
        safety.calls.clear();
        var resolved = evaluator(safety).answer(SESSION, triggered, triggered.clarificationId(), "safe");
        assertEquals(CheckinSafetyState.RESOLVED, resolved.state());
        assertFalse(resolved.blocking());
        assertEquals(List.of(Set.of()), safety.calls);
    }

    @Test
    void tieUsesDisplayOrderIndependentOfContributionOrdering() {
        var bindings = routeCatalog.bindings().stream()
            .sorted(Comparator.comparingInt(CheckinSafetyRouteCatalog.Binding::displayOrder)).limit(2).toList();
        var highOrder = bindings.stream().max(Comparator.comparingInt(CheckinSafetyRouteCatalog.Binding::displayOrder)).orElseThrow();
        var lowOrder = bindings.stream().min(Comparator.comparingInt(CheckinSafetyRouteCatalog.Binding::displayOrder)).orElseThrow();
        var safety = new RecordingSafety(routeCatalog);
        var result = evaluator(safety).evaluate(SESSION, Snapshot.clear(), score("12.000000",
            List.of(contribution(highOrder.itemCode(), "6.000000"), contribution(lowOrder.itemCode(), "6.000000"))));
        assertEquals(lowOrder.routeCode(), result.routeCode());
        assertEquals(List.of(Set.of(lowOrder.itemCode())), safety.calls);
    }

    @Property(tries = 120)
    void routeWinnerIsInvariantToContributionOrdering(
        @ForAll @IntRange(min = 6, max = 30) int a,
        @ForAll @IntRange(min = 6, max = 30) int b
    ) {
        var bindings = routeCatalog.bindings().stream()
            .sorted(Comparator.comparingInt(CheckinSafetyRouteCatalog.Binding::displayOrder)).limit(2).toList();
        var c1 = contribution(bindings.get(0).itemCode(), BigDecimal.valueOf(a).setScale(6).toPlainString());
        var c2 = contribution(bindings.get(1).itemCode(), BigDecimal.valueOf(b).setScale(6).toPlainString());
        var total = BigDecimal.valueOf(a + b).setScale(6).toPlainString();
        var first = evaluator(new RecordingSafety(routeCatalog)).evaluate(SESSION, Snapshot.clear(), score(total, List.of(c1, c2)));
        var second = evaluator(new RecordingSafety(routeCatalog)).evaluate(SESSION, Snapshot.clear(), score(total, List.of(c2, c1)));
        assertEquals(first.routeCode(), second.routeCode());
    }

    // ---- the same flow through the real composed platform engine ----

    @Test
    void everyBoundItemInEveryPeriodOpensItsRouteThroughThePlatformEngine() {
        var evaluator = new CheckinSafetyEvaluator(routeCatalog);
        for (var binding : routeCatalog.bindings()) {
            var result = evaluator.evaluate(SESSION, Snapshot.clear(),
                score("12.000000", List.of(contribution(binding.itemCode(), "12.000000"))));
            assertEquals(binding.routeCode(), result.routeCode(), binding.itemCode());
            assertEquals(CheckinSafetyState.CLARIFICATION_REQUIRED, result.state());
            assertEquals("MEDIUM", result.severity());
            assertTrue(result.blocking());
            assertEquals("checkin.safety.routes.v3:checkin.safety.rules.v3", result.decisionVersion());
            assertEquals(List.of("OPEN_SAFETY_CLARIFICATION"), result.allowedActions());
            assertEquals(CheckinSafetyClarificationFlow.clarificationId(SESSION, binding.routeCode(),
                result.decisionVersion()), result.clarification().get("clarificationId"));
            assertEquals(routeCatalog.route(binding.routeCode()).contentRef(), result.clarification().get("presentationKey"));
            assertEquals(List.of(Map.of("value", "safe", "labelKey", "checkin.safety.safe"),
                Map.of("value", "urgent", "labelKey", "checkin.safety.urgent")), result.clarification().get("options"));
        }
    }

    @Test
    void safeAnswerContinuesAndUrgentAnswerEscalatesToClinicalWithoutBlocking() {
        var evaluator = new CheckinSafetyEvaluator(routeCatalog);
        var triggered = evaluator.evaluate(SESSION, Snapshot.clear(),
            score("14.000000", List.of(contribution("M4", "14.000000"))));
        assertEquals("MENARCHE_DIZZINESS", triggered.routeCode());

        var safe = evaluator.answer(SESSION, triggered, triggered.clarificationId(), "safe");
        assertEquals(CheckinSafetyState.RESOLVED, safe.state());
        assertEquals("GENERAL_CONTINUE", safe.routeCode());
        assertEquals("NONE", safe.severity());
        assertFalse(safe.blocking());
        assertNull(safe.clarification());
        assertEquals(List.of("CONTINUE"), safe.allowedActions());
        assertEquals("checkin.safety.routes.v3:safety.baseline.v1", safe.decisionVersion());

        var urgent = evaluator.answer(SESSION, triggered, triggered.clarificationId(), "urgent");
        assertEquals(CheckinSafetyState.RESOLVED, urgent.state());
        assertEquals("CLINICAL_ESCALATION", urgent.routeCode());
        assertEquals("HIGH", urgent.severity());
        assertFalse(urgent.blocking());
        assertEquals(List.of("DEFER_AI", "CONTACT_CLINICIAN"), urgent.allowedActions());
        assertEquals("checkin.safety.routes.v3:safety.clinical.v1", urgent.decisionVersion());
    }

    @Test
    void wrongIdUnknownValueAndResolvedStateAreRejected() {
        var evaluator = new CheckinSafetyEvaluator(routeCatalog);
        var triggered = evaluator.evaluate(SESSION, Snapshot.clear(),
            score("12.000000", List.of(contribution("PP5", "12.000000"))));
        assertEquals("SAFETY_CLARIFICATION_NOT_CURRENT", assertThrows(CheckinException.class,
            () -> evaluator.answer(SESSION, triggered, "not-current", "safe")).code());
        assertEquals("SAFETY_VALUE_INVALID", assertThrows(CheckinException.class,
            () -> evaluator.answer(SESSION, triggered, triggered.clarificationId(), "skip")).code());
        assertEquals("SAFETY_CLARIFICATION_NOT_CURRENT", assertThrows(CheckinException.class,
            () -> evaluator.answer("session-2", triggered, triggered.clarificationId(), "safe")).code());
        var resolved = evaluator.answer(SESSION, triggered, triggered.clarificationId(), "safe");
        assertEquals("SAFETY_CLARIFICATION_NOT_CURRENT", assertThrows(CheckinException.class,
            () -> evaluator.answer(SESSION, resolved, triggered.clarificationId(), "safe")).code());
    }

    @Test
    void routeMismatchBetweenSelectionAndCoreFailsClosed() {
        var evaluator = new CheckinSafetyEvaluator(routeCatalog,
            signals -> new SafetyDecision(SafetyDisposition.ALLOW, "GENERAL_CONTINUE", SafetySeverity.NONE,
                Set.of("CONTINUE"), "safety.baseline.v1", "checkin.safety.routes.v3", "DETERMINISTIC", "NOT_APPLICABLE"),
            flow);
        assertEquals("SAFETY_ROUTE_VERSION_MISMATCH", assertThrows(CheckinException.class,
            () -> evaluator.evaluate(SESSION, Snapshot.clear(),
                score("12.000000", List.of(contribution("C4", "12.000000"))))).code());
    }

    private CheckinSafetyEvaluator evaluator(RecordingSafety safety) {
        return new CheckinSafetyEvaluator(routeCatalog, safety::evaluate, flow);
    }

    private static CheckinScoringResult.Contribution contribution(String itemCode, String safety) {
        return new CheckinScoringResult.Contribution(itemCode, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal(safety), new BigDecimal(safety), new BigDecimal(safety), List.of());
    }

    private static CheckinScoringResult score(String value, List<CheckinScoringResult.Contribution> contributions) {
        var safety = new BigDecimal(value).setScale(6);
        return new CheckinScoringResult(BigDecimal.ZERO.setScale(6), safety, CheckinAdjustmentTier.NONE,
            safety.compareTo(THRESHOLD) >= 0, contributions.size(), contributions.size(), contributions);
    }

    /** Same stand-in as the Kotlin RecordingSafety. */
    private static final class RecordingSafety {
        final List<Set<String>> calls = new ArrayList<>();
        private final CheckinSafetyRouteCatalog catalog;

        RecordingSafety(CheckinSafetyRouteCatalog catalog) { this.catalog = catalog; }

        SafetyDecision evaluate(Set<String> signals) {
            calls.add(Set.copyOf(signals));
            if (signals.isEmpty()) {
                return new SafetyDecision(SafetyDisposition.ALLOW, "GENERAL_CONTINUE", SafetySeverity.NONE,
                    Set.of("CONTINUE"), "safety.baseline.v1", "safety.catalog.v1", "DETERMINISTIC", "NOT_APPLICABLE");
            }
            if (signals.contains("URGENT_CLINICAL_RISK")) {
                return new SafetyDecision(SafetyDisposition.REDIRECT, "CLINICAL_ESCALATION", SafetySeverity.HIGH,
                    Set.of("CONTACT_CLINICIAN"), "safety.clinical.v1", "safety.catalog.v1", "DETERMINISTIC", "NOT_APPLICABLE");
            }
            var binding = catalog.bindingForItem(signals.iterator().next());
            var route = catalog.route(binding.routeCode());
            return new SafetyDecision(route.disposition(), route.code(), route.severity(), route.allowedActions(),
                catalog.metadata().ruleVersion(), catalog.metadata().catalogVersion(), "DETERMINISTIC", "NOT_APPLICABLE");
        }
    }
}
