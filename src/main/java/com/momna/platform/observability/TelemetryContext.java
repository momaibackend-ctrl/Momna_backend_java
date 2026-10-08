package com.momna.platform.observability;

import java.util.Map;

public record TelemetryContext(
    String requestId,
    String traceId,
    String sourceModule,
    String userId,
    String flowInstanceId,
    String contextSnapshotId,
    Map<String,String> versions
) {
    public TelemetryContext {
        requireTechnical(requestId, "requestId");
        requireTechnical(traceId, "traceId");
        requireTechnical(sourceModule, "sourceModule");
        if (userId != null) requireTechnical(userId, "userId");
        if (flowInstanceId != null) requireTechnical(flowInstanceId, "flowInstanceId");
        if (contextSnapshotId != null) requireTechnical(contextSnapshotId, "contextSnapshotId");
        versions = versions == null ? Map.of() : Map.copyOf(versions);
        if (versions.size() > 12) throw new IllegalArgumentException("Too many telemetry versions");
        versions.forEach((k,v) -> {
            requireTechnical(k, "version key");
            requireTechnical(v, "version value");
        });
    }

    public TelemetryContext forModule(String module) {
        return new TelemetryContext(
            requestId, traceId, module, userId, flowInstanceId, contextSnapshotId, versions
        );
    }

    private static void requireTechnical(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9_.:/-]{1,160}")) {
            throw new IllegalArgumentException(name + " must be a bounded technical identifier");
        }
    }
}
