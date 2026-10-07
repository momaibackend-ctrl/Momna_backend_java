package com.momna.modules.flow.application;

import com.momna.modules.flow.infrastructure.FlowDefinitionEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record FlowDefinitionModel(
    String key,
    int version,
    String resultKind,
    List<Step> steps
) {
    public record Step(String stepId, String fieldId, boolean required, boolean skippable) {}

    @SuppressWarnings("unchecked")
    public static FlowDefinitionModel from(FlowDefinitionEntity entity) {
        var json = entity.getDefinitionJson();
        var resultKind = String.valueOf(json.getOrDefault("resultKind", "FLOW"));
        var rawSteps = json.get("steps");
        if (!(rawSteps instanceof List<?> list) || list.isEmpty()) {
            throw new FlowException("VALIDATION", "Flow definition has no steps");
        }

        var steps = new ArrayList<Step>();
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
        }
        return new FlowDefinitionModel(entity.getDefinitionKey(), entity.getDefinitionVersion(), resultKind, List.copyOf(steps));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }
}
