package com.momna.modules.flow.application;

import java.math.BigDecimal;
import java.util.*;

public final class FlowConditionEvaluator {
    private FlowConditionEvaluator() {}

    public static boolean matches(
        FlowDefinitionModel.Condition condition,
        Map<String, Map<String, Object>> values
    ) {
        if (condition == null) return true;

        return switch (condition.type()) {
            case "LEAF" -> matchesLeaf(condition, values.get(condition.sourceFieldId()));
            case "ALL" -> !condition.items().isEmpty()
                && condition.items().stream().allMatch(item -> matches(item, values));
            case "ANY" -> !condition.items().isEmpty()
                && condition.items().stream().anyMatch(item -> matches(item, values));
            case "NOT" -> condition.items().size() == 1 && !matches(condition.items().getFirst(), values);
            case "ANY_MATCH" -> anyMatch(condition, values.get(condition.sourceFieldId()));
            default -> false;
        };
    }

    private static boolean matchesLeaf(
        FlowDefinitionModel.Condition condition,
        Map<String, Object> payload
    ) {
        var operator = condition.operator();
        var scalar = scalar(payload);
        var expected = expected(condition);

        return switch (operator) {
            case "EQUALS" -> Objects.equals(scalar, condition.expectedValue());
            case "NOT_EQUALS" -> scalar != null && !Objects.equals(scalar, condition.expectedValue());
            case "PRESENT" -> present(payload);
            case "ABSENT" -> !present(payload);
            case "IN" -> scalar != null && expected.contains(scalar);
            case "NOT_IN" -> scalar != null && !expected.contains(scalar);
            case "SELECTED_ANY_IN" -> selected(payload).stream().anyMatch(expected::contains);
            case "SELECTED_ALL_IN" -> !expected.isEmpty() && selected(payload).containsAll(expected);
            case "NUMBER_GTE" -> compareNumber(payload, condition.expectedValue(), true);
            case "NUMBER_LTE" -> compareNumber(payload, condition.expectedValue(), false);
            default -> false;
        };
    }

    private static boolean anyMatch(
        FlowDefinitionModel.Condition condition,
        Map<String, Object> payload
    ) {
        for (var element : elements(payload)) {
            Map<String, Object> target = element;
            if (condition.elementPath() != null && !condition.elementPath().isBlank()) {
                target = resolvePath(element, condition.elementPath());
                if (target == null) continue;
            }
            var nested = condition.items().isEmpty() ? null : condition.items().getFirst();
            if (nested != null && matchesLeaf(nested, target)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> elements(Map<String, Object> payload) {
        if (payload == null) return List.of();
        var raw = payload.get("value");
        if (raw instanceof List<?> list) {
            var result = new ArrayList<Map<String, Object>>();
            for (var item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                } else {
                    result.add(Map.of("value", item));
                }
            }
            return result;
        }
        if (raw instanceof Map<?, ?> map) {
            var result = new ArrayList<Map<String, Object>>();
            for (var value : map.values()) {
                if (value instanceof Map<?, ?> nested) {
                    result.add((Map<String, Object>) nested);
                } else {
                    result.add(Map.of("value", value));
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolvePath(Map<String, Object> payload, String path) {
        Object current = payload;
        for (var segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(segment);
        }
        if (current instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return current == null ? null : Map.of("value", current);
    }

    private static boolean present(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return false;
        return payload.get("value") != null || payload.get("reference") != null;
    }

    @SuppressWarnings("unchecked")
    private static String scalar(Map<String, Object> payload) {
        if (payload == null) return null;
        Object value = payload.get("value");

        if (value instanceof Map<?, ?> map) {
            var nestedValue = map.get("value");
            if (nestedValue != null && !(nestedValue instanceof Map<?, ?>) && !(nestedValue instanceof Collection<?>)) {
                return String.valueOf(nestedValue);
            }
            var kind = map.get("kind");
            if (kind != null && nestedValue != null) return String.valueOf(nestedValue);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }

        var reference = payload.get("reference");
        if (reference instanceof Map<?, ?> map) {
            var provider = map.get("provider");
            var id = map.get("id");
            if (provider != null && id != null) return provider + ":" + id;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> selected(Map<String, Object> payload) {
        if (payload == null) return Set.of();
        Object value = payload.get("value");
        if (value instanceof Map<?, ?> outer && outer.get("value") instanceof Map<?, ?> inner) {
            value = inner;
        }
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashSet<String>();
            for (var entry : map.entrySet()) {
                var selected = entry.getValue();
                if (Boolean.TRUE.equals(selected)
                    || "true".equalsIgnoreCase(String.valueOf(selected))
                    || "1".equals(String.valueOf(selected))
                    || "selected".equalsIgnoreCase(String.valueOf(selected))) {
                    result.add(String.valueOf(entry.getKey()));
                }
            }
            return result;
        }
        var scalar = scalar(payload);
        return scalar == null ? Set.of() : Set.of(scalar);
    }

    private static boolean compareNumber(
        Map<String, Object> payload,
        String expected,
        boolean gte
    ) {
        if (expected == null) return false;
        var raw = scalar(payload);
        if (raw == null) return false;
        try {
            var actual = new BigDecimal(raw);
            var target = new BigDecimal(expected);
            return gte ? actual.compareTo(target) >= 0 : actual.compareTo(target) <= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Set<String> expected(FlowDefinitionModel.Condition condition) {
        if (!condition.expectedValues().isEmpty()) return condition.expectedValues();
        return condition.expectedValue() == null ? Set.of() : Set.of(condition.expectedValue());
    }
}
