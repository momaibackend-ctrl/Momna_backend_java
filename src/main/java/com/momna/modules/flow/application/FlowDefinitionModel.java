package com.momna.modules.flow.application;

import com.momna.modules.flow.infrastructure.FlowDefinitionEntity;
import java.util.*;

public record FlowDefinitionModel(
    String key,
    int version,
    String resultKind,
    List<Step> steps,
    Map<String, Condition> conditions
) {
    public record Step(String stepId, String fieldId, boolean required, boolean skippable) {}

    public record Condition(
        String type,
        String sourceFieldId,
        String operator,
        String expectedValue,
        Set<String> expectedValues,
        List<Condition> items,
        String elementPath
    ) {}

    @SuppressWarnings("unchecked")
    public static FlowDefinitionModel from(FlowDefinitionEntity entity) {
        var json = entity.getDefinitionJson();
        var resultKind = String.valueOf(json.getOrDefault("resultKind", "FLOW"));
        var rawSteps = json.get("steps");
        if (!(rawSteps instanceof List<?> list) || list.isEmpty()) {
            throw new FlowException("VALIDATION", "Flow definition has no steps");
        }

        var steps = new ArrayList<Step>();
        var conditions = new LinkedHashMap<String, Condition>();
        for (var raw : list) {
            if (!(raw instanceof Map<?, ?> map)) {
                throw new FlowException("VALIDATION", "Invalid flow step");
            }
            var stepId = text(map.get("stepId"));
            var fieldId = text(map.get("fieldId"));
            var required = bool(map.get("required"), true);
            var skippable = bool(map.get("skippable"), false);
            if (stepId.isBlank() || fieldId.isBlank()) {
                throw new FlowException("VALIDATION", "Flow step identifiers are required");
            }
            steps.add(new Step(stepId, fieldId, required, skippable));

            var condition = parseCondition(map.get("condition"));
            if (condition == null && map.get("showWhen") instanceof List<?> showWhen && !showWhen.isEmpty()) {
                var leaves = new ArrayList<Condition>();
                for (var item : showWhen) {
                    var leaf = parseLegacyRule(item);
                    if (leaf != null) leaves.add(leaf);
                }
                if (!leaves.isEmpty()) {
                    condition = new Condition("ALL", null, null, null, Set.of(), List.copyOf(leaves), null);
                }
            }
            if (condition != null) conditions.put(fieldId, condition);
        }
        return new FlowDefinitionModel(
            entity.getDefinitionKey(),
            entity.getDefinitionVersion(),
            resultKind,
            List.copyOf(steps),
            Map.copyOf(conditions)
        );
    }

    @SuppressWarnings("unchecked")
    private static Condition parseCondition(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return null;
        var type = text(map.get("type")).toUpperCase(Locale.ROOT);
        if (type.isBlank() && map.get("operator") != null) type = "LEAF";

        if (type.equals("LEAF")) return parseLegacyRule(map);

        if (type.equals("ANY_MATCH")) {
            var nested = parseCondition(map.get("elementRule"));
            if (nested == null) nested = parseLegacyRule(map.get("elementRule"));
            return new Condition(
                "ANY_MATCH",
                text(map.get("collectionFieldId")),
                null,
                null,
                Set.of(),
                nested == null ? List.of() : List.of(nested),
                nullableText(map.get("elementPath"))
            );
        }

        if (type.equals("NOT")) {
            var nested = parseCondition(map.get("item"));
            return new Condition("NOT", null, null, null, Set.of(),
                nested == null ? List.of() : List.of(nested), null);
        }

        if (type.equals("ALL") || type.equals("ANY")) {
            var items = new ArrayList<Condition>();
            if (map.get("items") instanceof List<?> list) {
                for (var item : list) {
                    var parsed = parseCondition(item);
                    if (parsed != null) items.add(parsed);
                }
            }
            return new Condition(type, null, null, null, Set.of(), List.copyOf(items), null);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Condition parseLegacyRule(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return null;
        var source = text(map.get("sourceFieldId"));
        if (source.isBlank()) source = text(map.get("source"));
        var operator = text(map.get("operator")).toUpperCase(Locale.ROOT);
        if (source.isBlank() || operator.isBlank()) return null;

        var values = new LinkedHashSet<String>();
        if (map.get("expectedValues") instanceof Collection<?> collection) {
            collection.forEach(x -> values.add(String.valueOf(x)));
        }
        return new Condition(
            "LEAF",
            source,
            operator,
            nullableText(map.get("expectedValue")),
            Set.copyOf(values),
            List.of(),
            null
        );
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String nullableText(Object value) {
        var text = text(value);
        return text.isEmpty() ? null : text;
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }
}
