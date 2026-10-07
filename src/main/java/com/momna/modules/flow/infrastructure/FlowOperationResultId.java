package com.momna.modules.flow.infrastructure;

import java.io.Serializable;
import java.util.UUID;

public class FlowOperationResultId implements Serializable {
    public UUID instanceId;
    public String operationId;

    public FlowOperationResultId() {}

    public FlowOperationResultId(UUID instanceId, String operationId) {
        this.instanceId = instanceId;
        this.operationId = operationId;
    }
}
