package com.momna.platform.safety;

import java.util.*;
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
    /**
     * Shared canonical safety resolver for the medical check-in workflow.
     * No AI may decide a disposition. Only catalog-listed facts are evaluated.
     * Clinical release is gated separately from this technical implementation.
     */
    public MedicalStep evaluateMedical(
        MedicalSafetyCatalog.Route route,
        MedicalSafetyCatalog catalog,
        java.util.Map<String, String> recorded,
        java.util.Map<String, String> alreadyKnown,
        boolean skipRemaining
    ) {
        var known = new LinkedHashMap<String, String>(alreadyKnown);
        known.putAll(recorded);
        var questions = new ArrayList<String>(route.questions());
        if (route.routeId().equals("R9") && questions.contains("F_REDUCED_MOVEMENTS")) {
            // Established movement is context, not equivalent to NO when missing.
            questions.add(questions.indexOf("F_REDUCED_MOVEMENTS"), "F_MOVEMENTS_ESTABLISHED");
        }
        if (java.util.Set.of("R4", "R20").contains(route.routeId())
            && "YES".equals(known.get("F_SEVERE_ABDO_PELVIC_PAIN"))) {
            var index = questions.indexOf("F_SEVERE_ABDO_PELVIC_PAIN");
            if (index >= 0 && !questions.contains("F_PREGNANCY_POSSIBLE")) {
                questions.add(index + 1, "F_PREGNANCY_POSSIBLE");
            }
        }
        if (route.routeId().equals("R14")
            && ("NO".equals(known.get("F_FEVER_38")) || "UNSURE".equals(known.get("F_FEVER_38")))) {
            questions.add("F_CHILLS");
        }

        var emergency = hasYes(known, "F_BREATHING_SEVERE", "F_CHEST_PAIN", "F_FAINTED",
            "F_SEIZURE", "F_NEURO_DEFICIT", "F_THUNDERCLAP_HEADACHE",
            "F_AIRWAY_SWELLING", "F_BLEED_SUDDEN_INCREASE",
            "F_BREATHLESS_SUDDEN_OR_REST");
        var comboAirway = "YES".equals(known.get("F_AIRWAY_SWELLING"))
            || ("YES".equals(known.get("F_BREATHING_SEVERE"))
                && "YES".equals(known.get("F_LIP_FACE_SWELLING")));
        var pregnancyPossible = route.period().equals("PLANNING")
            || ("YES".equals(known.get("F_PREGNANCY_POSSIBLE")) || "UNSURE".equals(known.get("F_PREGNANCY_POSSIBLE")));
        var comboPain = "YES".equals(known.get("F_SEVERE_ABDO_PELVIC_PAIN"))
            && pregnancyPossible;
        var presyncope = hasYes(known, "F_PRESYNCOPE", "F_FAINTED");
        var comboShock = (comboPain && presyncope)
            || (presyncope && hasYes(known, "F_PAD_SOAK_1H", "F_BLEED_SUDDEN_INCREASE"));
        if (comboShock) emergency = true;
        if (emergency) {
            return step(route, "COMPLETE", "EMERGENCY", comboAirway ? "AG1" : "E",
                "CALL_EMERGENCY", null, "CLINICAL_RELEASE_PENDING");
        }
        if ("YES".equals(known.get("F_HARM_THOUGHTS"))) {
            return step(route, "COMPLETE", "SAFETY_ASSESSMENT", "PM2", "ACKNOWLEDGE",
                null, "PM2_CLINICAL_PROTOCOL_PENDING");
        }

        var urgent = comboPain;
        var prompt = false;
        var routine = false;
        var unsure = false;
        var conditionUndefined = false;
        for (var factCode : known.keySet()) {
            if (!catalog.facts().stream().anyMatch(f -> f.code().equals(factCode))) continue;
            var answer = known.get(factCode);
            var fact = catalog.fact(factCode);
            if ("UNSURE".equals(answer)
                && java.util.Set.of("URGENT", "EMERGENCY").contains(fact.role())) unsure = true;
            if (!"YES".equals(answer)) continue;
            switch (fact.role()) {
                case "URGENT" -> urgent = true;
                case "PROMPT" -> prompt = true;
                case "ROUTINE" -> routine = true;
                default -> {}
            }
            if (fact.rule().contains("D3") || fact.rule().contains("клиницист")) {
                conditionUndefined = true;
            }
        }
        if (route.routeId().equals("R12") && "YES".equals(known.get("F_PRESYNCOPE"))) urgent = true;
        if (route.routeId().equals("R17") && "YES".equals(known.get("F_BP_MEASURED"))
            && !known.containsKey("BP_READING")) {
            return step(route, "QUESTION", urgent ? "URGENT" : "PENDING", "BP1",
                "ENTER_BLOOD_PRESSURE", "BP_READING", "BP_THRESHOLDS_NOT_CLINICALLY_APPROVED");
        }

        if (!skipRemaining) {
            for (var code : questions) {
                if (known.containsKey(code)) continue;
                if (code.equals("F_REDUCED_MOVEMENTS")
                    && !"YES".equals(known.get("F_MOVEMENTS_ESTABLISHED"))) continue;
                // If a user has reported an urgent sign, continue only for possible
                // escalation or a dangerous combination (L3/D8).
                if (urgent && !needsFurtherUrgentCheck(route, code, catalog, known)) continue;
                if (prompt && route.routeId().equals("R18")) break;
                return step(route, "QUESTION", urgent ? "URGENT" : prompt ? "PROMPT" : "PENDING",
                    "Q", "ANSWER_FACT", code,
                    conditionUndefined ? "CONDITION_UNDEFINED" : "CLINICAL_RELEASE_PENDING");
            }
        }

        if (comboPain) return step(route, "COMPLETE", "URGENT", "EP1", "ACKNOWLEDGE",
            null, "COMBO_CLINICAL_APPROVAL_PENDING");
        if (urgent) {
            String screen = route.period().equals("PREGNANCY")
                && !"YES".equals(known.get("F_LIP_FACE_SWELLING")) ? "OB1" : "U";
            return step(route, "COMPLETE", "URGENT", screen,
                screen.equals("OB1") ? "CONTACT_MATERNITY" : "ACKNOWLEDGE", null,
                conditionUndefined ? "CONDITION_UNDEFINED" : "CLINICAL_RELEASE_PENDING");
        }
        if (prompt) {
            var screen = route.routeId().equals("R18") ? "PM1"
                : route.routeId().equals("R24") ? "MP1"
                : "YES".equals(known.get("D_POSTMENOPAUSAL_BLEED")) ? "MB1" : "P";
            return step(route, "COMPLETE", "PROMPT", screen, "ACKNOWLEDGE", null,
                "CLINICAL_RELEASE_PENDING");
        }
        if (routine) return step(route, "COMPLETE", "ROUTINE",
            route.routeId().equals("R23") ? "GS1" : "R", "ACKNOWLEDGE", null,
            "CLINICAL_RELEASE_PENDING");
        if (unsure) return step(route, "COMPLETE", "UNKNOWN", "N", "REVIEW_ANSWER",
            null, "INFORMATION_INSUFFICIENT");
        return step(route, "COMPLETE", "NOT_REPORTED", "H1", "OPEN_ER0", null,
            "CLINICAL_RELEASE_PENDING");
    }

    private boolean needsFurtherUrgentCheck(MedicalSafetyCatalog.Route route, String factCode,
        MedicalSafetyCatalog catalog, java.util.Map<String, String> known) {
        if (catalog.fact(factCode).role().equals("EMERGENCY")
            || catalog.fact(factCode).role().equals("SAFETY_ASSESSMENT")) return true;
        if (java.util.Set.of("R4", "R20", "R28", "R3", "R12").contains(route.routeId())
            && java.util.Set.of("F_SEVERE_ABDO_PELVIC_PAIN", "F_PREGNANCY_POSSIBLE",
                "F_PRESYNCOPE", "F_FAINTED", "F_PAD_SOAK_1H", "F_BLEED_SUDDEN_INCREASE")
                .contains(factCode)) return true;
        return route.routeId().equals("R17") && factCode.equals("F_BP_MEASURED");
    }

    private boolean hasYes(java.util.Map<String, String> answers, String... codes) {
        for (var code : codes) if ("YES".equals(answers.get(code))) return true;
        return false;
    }

    private MedicalStep step(MedicalSafetyCatalog.Route route, String state, String disposition,
        String screen, String action, String nextFact, String reviewStatus) {
        return new MedicalStep(route.routeId(), route.routeCode(),
            MedicalSafetyCatalog.VERSION, MedicalSafetyCatalog.RULE_VERSION,
            state, disposition, screen, action, nextFact, reviewStatus);
    }

    public record MedicalStep(String routeId, String routeCode, String catalogVersion,
        String ruleVersion, String state, String disposition, String screenCode,
        String actionCode, String nextFactCode, String conditionStatus) {}

}
