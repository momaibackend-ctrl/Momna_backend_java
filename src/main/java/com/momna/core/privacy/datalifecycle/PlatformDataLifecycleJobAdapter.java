package com.momna.core.privacy.datalifecycle;

import com.momna.platform.jobs.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PlatformDataLifecycleJobAdapter implements DataLifecycleJobPort {
    private final DurableJobQueue jobs;

    public PlatformDataLifecycleJobAdapter(DurableJobQueue jobs) {
        this.jobs = jobs;
    }

    @Override
    public void enqueue(UUID operationId, DataLifecycleOperationKind kind, String traceId) {
        var type = kind == DataLifecycleOperationKind.EXPORT
            ? JobType.EXPORT
            : JobType.FILE_PROCESSING;

        jobs.enqueue(
            type,
            "data-lifecycle:" + operationId,
            "data-lifecycle:" + operationId,
            traceId,
            3,
            Duration.ofMinutes(2),
            1
        );
    }
}
