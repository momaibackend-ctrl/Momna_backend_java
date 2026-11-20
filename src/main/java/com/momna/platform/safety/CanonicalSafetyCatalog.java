package com.momna.platform.safety;

import java.util.List;
import java.util.Set;

/** The platform base catalog every feature contribution composes onto. */
public final class CanonicalSafetyCatalog {
    public static final String VERSION = "safety.catalog.v1";
    public static final String BASELINE_RULE_VERSION = "safety.baseline.v1";
    public static final String CRITICAL_RULE_VERSION = "safety.critical.v1";
    public static final String CLINICAL_RULE_VERSION = "safety.clinical.v1";
    public static final String RELATIONSHIP_RULE_VERSION = "safety.relationship.v1";

    public static final String CONTINUE = "GENERAL_CONTINUE";
    public static final String SUPPORT = "SAFETY_SUPPORT";
    public static final String CLINICAL = "CLINICAL_ESCALATION";
    public static final String EMERGENCY = "EMERGENCY_ESCALATION";
    public static final String REVIEW = "SAFETY_REVIEW_REQUIRED";

    private CanonicalSafetyCatalog() {}

    public static SafetyCatalog create() {
        return new SafetyCatalog(
            VERSION,
            Set.of(VERSION),
            List.of(
                new SafetyRoute(CONTINUE, SafetyDisposition.ALLOW, SafetySeverity.NONE, ordered("CONTINUE")),
                new SafetyRoute(SUPPORT, SafetyDisposition.REDIRECT, SafetySeverity.HIGH, ordered("DEFER_AI", "CONTACT_SUPPORT")),
                new SafetyRoute(CLINICAL, SafetyDisposition.REDIRECT, SafetySeverity.HIGH, ordered("DEFER_AI", "CONTACT_CLINICIAN")),
                new SafetyRoute(EMERGENCY, SafetyDisposition.BLOCK, SafetySeverity.CRITICAL, ordered("CONTACT_EMERGENCY_SERVICES")),
                new SafetyRoute(REVIEW, SafetyDisposition.REDIRECT, SafetySeverity.MEDIUM, ordered("DEFER_AI", "CONTACT_SUPPORT"))
            ),
            List.of(
                new SafetyRule("immediate-danger", CRITICAL_RULE_VERSION, ordered("IMMEDIATE_DANGER", "SELF_HARM_INTENT"), EMERGENCY),
                new SafetyRule("medical-emergency", CRITICAL_RULE_VERSION, ordered("MEDICAL_EMERGENCY"), EMERGENCY),
                new SafetyRule("urgent-clinical-risk", CLINICAL_RULE_VERSION, ordered("URGENT_CLINICAL_RISK"), CLINICAL),
                new SafetyRule("abuse-or-coercion", RELATIONSHIP_RULE_VERSION, ordered("ABUSE_OR_COERCION"), SUPPORT)
            ),
            ordered(BASELINE_RULE_VERSION, CRITICAL_RULE_VERSION, CLINICAL_RULE_VERSION, RELATIONSHIP_RULE_VERSION),
            BASELINE_RULE_VERSION,
            CONTINUE,
            REVIEW
        );
    }

    private static Set<String> ordered(String... values) {
        return new java.util.LinkedHashSet<>(List.of(values));
    }
}
