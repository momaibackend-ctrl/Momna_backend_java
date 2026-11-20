package com.momna.platform.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Ported from the working Kotlin SafetyPlatformTest (deterministic cases; no AI classifier in Java). */
class SafetyPlatformTest {
    private final CanonicalSafetyEngine engine = new CanonicalSafetyEngine(CanonicalSafetyCatalog.create());

    @Test
    void criticalDeterministicRuleBlocks() {
        var decision = engine.evaluate(Set.of("SELF_HARM_INTENT"), true);
        assertEquals(SafetyDisposition.BLOCK, decision.decision());
        assertEquals(CanonicalSafetyCatalog.EMERGENCY, decision.route());
        assertEquals(SafetySeverity.CRITICAL, decision.severity());
        assertEquals(CanonicalSafetyEngine.SOURCE_DETERMINISTIC, decision.source());
        assertEquals(CanonicalSafetyCatalog.CRITICAL_RULE_VERSION, decision.ruleVersion());
    }

    @Test
    void mostSevereMatchingRuleWins() {
        var decision = engine.evaluate(Set.of("ABUSE_OR_COERCION", "URGENT_CLINICAL_RISK", "MEDICAL_EMERGENCY"), false);
        assertEquals(CanonicalSafetyCatalog.EMERGENCY, decision.route());
        assertTrue(decision.blocked());
    }

    @Test
    void noSignalIsBaselineContinue() {
        var decision = engine.evaluate(Set.of(), false);
        assertEquals(CanonicalSafetyCatalog.CONTINUE, decision.route());
        assertEquals(SafetySeverity.NONE, decision.severity());
        assertEquals(CanonicalSafetyCatalog.BASELINE_RULE_VERSION, decision.ruleVersion());
        assertEquals(Set.of("CONTINUE"), decision.allowedActions());
    }

    @Test
    void requestedClassificationWithoutClassifierFailsClosedToReview() {
        var decision = engine.evaluate(Set.of(), true);
        assertEquals(CanonicalSafetyCatalog.REVIEW, decision.route());
        assertEquals(SafetyDisposition.REDIRECT, decision.decision());
        assertEquals(CanonicalSafetyEngine.SOURCE_FALLBACK, decision.source());
        assertEquals(CanonicalSafetyEngine.FALLBACK_CLASSIFIER_NOT_CONFIGURED, decision.fallbackState());
    }

    @Test
    void catalogRejectsAnUnregisteredRuleVersion() {
        assertThrows(IllegalArgumentException.class, () -> new SafetyCatalog(
            "catalog.v1", Set.of("catalog.v1"),
            List.of(new SafetyRoute("BASELINE_ROUTE", SafetyDisposition.ALLOW, SafetySeverity.NONE, Set.of("CONTINUE"))),
            List.of(new SafetyRule("bad-rule", "unknown.v1", Set.of("KNOWN_SIGNAL"), "BASELINE_ROUTE")),
            Set.of("baseline.v1"), "baseline.v1", "BASELINE_ROUTE", "BASELINE_ROUTE"));
    }

    @Test
    void catalogRejectsAnUnregisteredCatalogVersion() {
        assertThrows(IllegalArgumentException.class, () -> new SafetyCatalog(
            "catalog.v2", Set.of("catalog.v1"),
            List.of(new SafetyRoute("BASELINE_ROUTE", SafetyDisposition.ALLOW, SafetySeverity.NONE, Set.of("CONTINUE"))),
            List.of(), Set.of("baseline.v1"), "baseline.v1", "BASELINE_ROUTE", "BASELINE_ROUTE"));
    }

    @Test
    void serviceAndEngineShareTheBaseCatalog() {
        var service = new CanonicalSafetyService().evaluate(Set.of("ABUSE_OR_COERCION"));
        var direct = engine.evaluate(Set.of("ABUSE_OR_COERCION"), false);
        assertEquals(CanonicalSafetyCatalog.SUPPORT, service.route());
        assertEquals(direct, service);
        assertTrue(service.allowedActions().contains("CONTACT_SUPPORT"));
        assertEquals(CanonicalSafetyCatalog.VERSION, service.catalogVersion());
    }
}
