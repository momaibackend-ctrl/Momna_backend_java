package com.momna.modules.flow.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "flow_operation_results", schema = "momna")
@IdClass(FlowOperationResultId.class)
public class FlowOperationResultEntity {
    @Id
    @Column(name = "instance_id")
    private UUID instanceId;

    @Id
    @Column(name = "operation_id")
    private String operationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected FlowOperationResultEntity() {}

    public FlowOperationResultEntity(UUID instanceId, String operationId, Map<String, Object> resultJson) {
        this.instanceId = instanceId;
        this.operationId = operationId;
        this.resultJson = resultJson;
    }

    public UUID getInstanceId() { return instanceId; }
    public String getOperationId() { return operationId; }
    public Map<String, Object> getResultJson() { return resultJson; }
}
