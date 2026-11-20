package com.momna.platform.safety;

import java.util.Comparator;
import java.util.Set;

/**
 * Deterministic safety evaluation over a composed catalog. The matching rule whose route is
 * most severe wins (ties by rule id). With no match the baseline route applies, unless an AI
 * classification was requested: no classifier is configured, so that fails closed to review.
 */
public final class CanonicalSafetyEngine {
    public static final String SOURCE_DETERMINISTIC = "DETERMINISTIC";
    public static final String SOURCE_FALLBACK = "FALLBACK";
    public static final String FALLBACK_NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String FALLBACK_CLASSIFIER_NOT_CONFIGURED = "CLASSIFIER_NOT_CONFIGURED";

    private final SafetyCatalog catalog;

    public CanonicalSafetyEngine(SafetyCatalog catalog) {
        this.catalog = catalog;
    }

    public SafetyCatalog catalog() { return catalog; }

    public SafetyDecision evaluate(Set<String> signalKeys, boolean aiClassificationRequested) {
        var deterministic = catalog.rules().stream()
            .filter(rule -> rule.signalKeys().stream().anyMatch(signalKeys::contains))
            .max(Comparator
                .comparingInt((SafetyRule rule) -> catalog.route(rule.routeCode()).severity().ordinal())
                .thenComparing(SafetyRule::id));
        if (deterministic.isPresent()) {
            return decision(deterministic.get().routeCode(), deterministic.get().version(),
                SOURCE_DETERMINISTIC, FALLBACK_NOT_APPLICABLE);
        }
        if (!aiClassificationRequested) {
            return decision(catalog.baselineRouteCode(), catalog.baselineRuleVersion(),
                SOURCE_DETERMINISTIC, FALLBACK_NOT_APPLICABLE);
        }
        return decision(catalog.fallbackRouteCode(), catalog.baselineRuleVersion(),
            SOURCE_FALLBACK, FALLBACK_CLASSIFIER_NOT_CONFIGURED);
    }

    private SafetyDecision decision(String routeCode, String ruleVersion, String source, String fallback) {
        var route = catalog.route(routeCode);
        return new SafetyDecision(
            route.disposition(),
            route.code(),
            route.severity(),
            route.allowedActions(),
            ruleVersion,
            catalog.version(),
            source,
            fallback
        );
    }
}
