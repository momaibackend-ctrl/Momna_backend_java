package com.momna.modules.flow.infrastructure;

import com.momna.modules.flow.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "flow_instances",
    schema = "momna",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "definition_key", "start_operation_id"})
)
public class FlowInstanceEntity {
    @Id
    @Column(name = "instance_id")
    private UUID instanceId;

    @Column(name = "definition_key", nullable = false)
    private String definitionKey;

    @Column(name = "definition_version", nullable = false)
    private int definitionVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false)
    private FlowType flowType;

    @Column(name = "user_id", nullable = false)
    private String userId;

    private String variant;
    private String period;
    private String substage;

    @Column(name = "transition_scope_id")
    private String transitionScopeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlowInstanceStatus status;

    @Column(name = "current_step_id")
    private String currentStepId;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "start_operation_id", nullable = false)
    private String startOperationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FlowInstanceEntity() {}

    public FlowInstanceEntity(
        UUID instanceId,
        String definitionKey,
        int definitionVersion,
        FlowType flowType,
        String userId,
        String variant,
        String period,
        String substage,
        String transitionScopeId,
        FlowInstanceStatus status,
        String currentStepId,
        String startOperationId,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.instanceId = instanceId;
        this.definitionKey = definitionKey;
        this.definitionVersion = definitionVersion;
        this.flowType = flowType;
        this.userId = userId;
        this.variant = variant;
        this.period = period;
        this.substage = substage;
        this.transitionScopeId = transitionScopeId;
        this.status = status;
        this.currentStepId = currentStepId;
        this.startOperationId = startOperationId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getInstanceId() { return instanceId; }
    public String getDefinitionKey() { return definitionKey; }
    public int getDefinitionVersion() { return definitionVersion; }
    public FlowType getFlowType() { return flowType; }
    public String getUserId() { return userId; }
    public String getVariant() { return variant; }
    public String getPeriod() { return period; }
    public String getSubstage() { return substage; }
    public String getTransitionScopeId() { return transitionScopeId; }
    public FlowInstanceStatus getStatus() { return status; }
    public String getCurrentStepId() { return currentStepId; }
    public long getRowVersion() { return rowVersion; }
    public String getStartOperationId() { return startOperationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void moveTo(String stepId, Instant at) {
        this.currentStepId = stepId;
        this.updatedAt = at;
    }

    public void complete(Instant at) {
        this.status = FlowInstanceStatus.COMPLETED;
        this.currentStepId = null;
        this.updatedAt = at;
    }
}
