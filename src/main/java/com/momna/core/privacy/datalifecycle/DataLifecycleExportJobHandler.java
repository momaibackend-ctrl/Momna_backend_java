package com.momna.core.privacy.datalifecycle;

import com.momna.platform.jobs.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DataLifecycleExportJobHandler implements JobHandler {
    private static final String PREFIX = "data-lifecycle:";

    private final DataLifecycleOperationProcessor lifecycle;

    public DataLifecycleExportJobHandler(DataLifecycleOperationProcessor lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    public JobType type() {
        return JobType.EXPORT;
    }

    @Override
    public JobResult handle(JobRecord job) {
        if (!job.payloadRef().startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unexpected data lifecycle export payloadRef");
        }
        var id = UUID.fromString(job.payloadRef().substring(PREFIX.length()));
        var operation = lifecycle.process(id);
        return new JobResult("data-lifecycle-operation:" + operation.getOperationId());
    }
}
