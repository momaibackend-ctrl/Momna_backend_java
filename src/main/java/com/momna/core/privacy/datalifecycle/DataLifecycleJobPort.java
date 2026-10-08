package com.momna.core.privacy.datalifecycle;

import java.util.UUID;

public interface DataLifecycleJobPort {
    void enqueue(UUID operationId, DataLifecycleOperationKind kind, String traceId);
}
