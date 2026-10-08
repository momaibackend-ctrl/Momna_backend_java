package com.momna.platform.safety;

import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CanonicalSafetyService {
    public static final String CATALOG_VERSION = "safety.catalog.v1";
    public static final String BASELINE_RULE_VERSION = "safety.baseline.v1";
    public static final String CRITICAL_RULE_VERSION = "safety.critical.v1";
    public static final String CLINICAL_RULE_VERSION = "safety.clinical.v1";
    public static final String RELATIONSHIP_RULE_VERSION = "safety.relationship.v1";

    public SafetyDecision evaluate(Set<String> signalKeys) {
        if (signalKeys.contains("IMMEDIATE_DANGER") || signalKeys.contains("SELF_HARM_INTENT")
            || signalKeys.contains("MEDICAL_EMERGENCY")) {
            return new SafetyDecision(
                SafetyDisposition.BLOCK,
                "EMERGENCY_ESCALATION",
                SafetySeverity.CRITICAL,
                Set.of("CONTACT_EMERGENCY_SERVICES"),
                CRITICAL_RULE_VERSION,
                CATALOG_VERSION,
                "DETERMINISTIC",
                "NOT_APPLICABLE"
            );
        }
        if (signalKeys.contains("URGENT_CLINICAL_RISK")) {
            return new SafetyDecision(
                SafetyDisposition.REDIRECT,
                "CLINICAL_ESCALATION",
                SafetySeverity.HIGH,
                Set.of("DEFER_AI", "CONTACT_CLINICIAN"),
                CLINICAL_RULE_VERSION,
                CATALOG_VERSION,
                "DETERMINISTIC",
                "NOT_APPLICABLE"
            );
        }
        if (signalKeys.contains("ABUSE_OR_COERCION")) {
            return new SafetyDecision(
                SafetyDisposition.REDIRECT,
                "SAFETY_SUPPORT",
                SafetySeverity.HIGH,
                Set.of("DEFER_AI", "CONTACT_SUPPORT"),
                RELATIONSHIP_RULE_VERSION,
                CATALOG_VERSION,
                "DETERMINISTIC",
                "NOT_APPLICABLE"
            );
        }
        return new SafetyDecision(
            SafetyDisposition.ALLOW,
            "GENERAL_CONTINUE",
            SafetySeverity.NONE,
            Set.of("CONTINUE"),
            BASELINE_RULE_VERSION,
            CATALOG_VERSION,
            "DETERMINISTIC",
            "NOT_APPLICABLE"
        );
    }
}
