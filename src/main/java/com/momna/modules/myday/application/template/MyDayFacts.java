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

        /**
         * The answer per child: {@code {childRef: code}} and {@code [{childRef, value}]} keep their child
         * references; a single scalar answer, or a list of plain codes, is keyed "*" / by position.
         */
        public Map<String, String> perChild() {
            var raw = unwrap(payload);
            var out = new TreeMap<String, String>();
            if (raw instanceof Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    var value = unwrap(entry.getValue());
                    if (value != null && !(value instanceof Map<?, ?>) && !(value instanceof Collection<?>)
                        && !(value instanceof Boolean)) {
                        out.put(String.valueOf(entry.getKey()), String.valueOf(value));
                    }
                }
            } else if (raw instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    var item = list.get(i);
                    if (item instanceof Map<?, ?> element) {
                        var child = element.containsKey("childRef") ? element.get("childRef") : element.get("child");
                        var value = unwrap(element.get("value"));
                        if (value != null && !(value instanceof Map<?, ?>) && !(value instanceof Collection<?>)) {
                            out.put(child == null ? String.valueOf(i + 1) : String.valueOf(child), String.valueOf(value));
                        }
                    } else if (item != null) {
                        out.put(String.valueOf(i + 1), String.valueOf(unwrap(item)));
                    }
                }
            } else if (raw != null && !(raw instanceof Boolean)) {
                out.put("*", String.valueOf(raw));
            }
            return Collections.unmodifiableMap(out);
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
