package com.momna.core.privacy.datalifecycle;

import com.momna.platform.jobs.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DataLifecycleJobHandler implements JobHandler {
    private static final String PREFIX = "data-lifecycle:";

    private final DataLifecycleOperationProcessor lifecycle;

    public DataLifecycleJobHandler(DataLifecycleOperationProcessor lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    public JobType type() {
        return JobType.FILE_PROCESSING;
    }

    @Override
    public JobResult handle(JobRecord job) {
        if (!job.payloadRef().startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unexpected data lifecycle payloadRef");
        }
        var id = UUID.fromString(job.payloadRef().substring(PREFIX.length()));
        var operation = lifecycle.process(id);
        return new JobResult("data-lifecycle-operation:" + operation.getOperationId());
    }
}
