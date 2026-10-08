package com.momna.platform.observability;

import com.momna.platform.ai.AiContracts;
import com.momna.platform.jobs.JobRecord;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TelemetryCorrelation {
    private TelemetryCorrelation() {}

    public static TelemetryContext fromJob(
        JobRecord job,
        String sourceModule
    ) {
        return new TelemetryContext(
            job.idempotencyKey(),
            job.traceId(),
            sourceModule,
            null,
            null,
            null,
            Map.of(
                "job_payload_schema",
                "v" + job.payloadSchemaVersion()
            )
        );
    }

    public static TelemetryContext fromAi(
        AiContracts.Request request,
        String sourceModule
    ) {
        var versions = new LinkedHashMap<String,String>();
        if (request.promptVersion() != null) {
            versions.put("prompt", "v" + request.promptVersion());
        }
        if (request.modelPolicyVersion() != null) {
            versions.put(
                "model_policy",
                "v" + request.modelPolicyVersion()
            );
        }

        return new TelemetryContext(
            request.requestId(),
            request.traceId(),
            sourceModule == null ? "platform.ai" : sourceModule,
            null,
            null,
            request.contextSnapshotId(),
            versions
        );
    }

    public static TelemetryContext fromAi(
        AiContracts.Request request
    ) {
        return fromAi(request, "platform.ai");
    }
}
