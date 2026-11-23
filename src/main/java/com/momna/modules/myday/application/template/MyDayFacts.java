package com.momna.modules.myday.application.template;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Typed facts My Day reads at generation time. They are personal: they feed the overlay adapter
 * and the calendar facts, and never reach the prompt or the template key themselves.
 */
public final class MyDayFacts {
    private MyDayFacts() {}

    /**
     * One answered Check-in item of a final source session. {@code phaseShare} is the phase's base share
     * of this day (Morning(D) 0.7 / Evening(D-1) 0.3 when both took part, 1.0 when it is the only one).
     */
    public record Observation(String phase, String itemCode, int statePoints, BigDecimal phaseShare) {
        public Observation {
            Objects.requireNonNull(phase);
            Objects.requireNonNull(itemCode);
            Objects.requireNonNull(phaseShare);
            if (statePoints < 0) throw new IllegalArgumentException("statePoints must not be negative");
        }
    }

    /** The authoritative SafetyDecision projected from a final source session (CORE-BE-12 / MOMNA-1145). */
    public record SafetyProjection(String phase, String routeCode, String severity, String decisionVersion) {}

    /** One onboarding answer: the stored payload and when it was given. */
    public record OnboardingFact(Object payload, Instant answeredAt) {
        /**
         * The option codes of the answer. Payloads are {@code {"value": x}} with an optional typed wrapper
         * {@code {"kind": .., "value": x}}; x is a scalar, a list, or an object whose truthy keys count.
         */
        public Set<String> options() {
            var raw = unwrap(payload);
            var out = new TreeSet<String>();
            collect(raw, out, 0);
            return Collections.unmodifiableSet(out);
        }

        /**
         * Scalars count as options; a list contributes its elements; an object of flags ({@code {code: true}})
         * contributes its true keys; an object of answers per child ({@code {childRef: code}} or
         * {@code [{childRef, value}]}) contributes the answers, never the child references.
         */
        private static void collect(Object value, Set<String> out, int depth) {
            if (value == null || depth > 4) return;
            var raw = unwrap(value);
            if (raw instanceof Collection<?> list) {
                for (var item : list) collect(item, out, depth + 1);
            } else if (raw instanceof Map<?, ?> map) {
                if (map.containsKey("value")) {
                    collect(map.get("value"), out, depth + 1);
                    return;
                }
                for (var entry : map.entrySet()) {
                    var item = entry.getValue();
                    if (item instanceof Boolean flag) {
                        if (flag) out.add(String.valueOf(entry.getKey()));
                    } else {
                        collect(item, out, depth + 1);
                    }
                }
            } else if (!(raw instanceof Boolean)) {
                out.add(String.valueOf(raw));
            }
        }

        /** The single scalar value, if the answer is one. */
        public Optional<String> scalar() {
            var raw = unwrap(payload);
            if (raw == null || raw instanceof Collection<?> || raw instanceof Map<?, ?>) return Optional.empty();
            return Optional.of(String.valueOf(raw));
        }

        private static Object unwrap(Object value) {
            var current = value;
            for (int depth = 0; depth < 3 && current instanceof Map<?, ?> map; depth++) {
                if (map.containsKey("value") && (map.size() == 1 || map.containsKey("kind"))) {
                    current = map.get("value");
                } else {
                    break;
                }
            }
            return current;
        }
    }
}
