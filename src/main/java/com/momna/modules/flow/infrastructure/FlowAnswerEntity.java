package com.momna.modules.flow.infrastructure;

import com.momna.modules.flow.domain.FlowAnswerStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "flow_answers", schema = "momna")
public class FlowAnswerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "field_id", nullable = false)
    private String fieldId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_status", nullable = false)
    private FlowAnswerStatus answerStatus;

    @Column(name = "operation_id", nullable = false)
    private String operationId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(nullable = false)
    private boolean active;

    protected FlowAnswerEntity() {}

    public FlowAnswerEntity(
        UUID instanceId,
        String fieldId,
        Map<String, Object> payload,
        FlowAnswerStatus answerStatus,
        String operationId,
        Instant answeredAt
    ) {
        this.instanceId = instanceId;
        this.fieldId = fieldId;
        this.payload = payload;
        this.answerStatus = answerStatus;
        this.operationId = operationId;
        this.sourceType = "FLOW";
        this.answeredAt = answeredAt;
        this.active = true;
    }

    public Long getAnswerId() { return answerId; }
    public UUID getInstanceId() { return instanceId; }
    public String getFieldId() { return fieldId; }
    public Map<String, Object> getPayload() { return payload; }
    public FlowAnswerStatus getAnswerStatus() { return answerStatus; }
    public String getOperationId() { return operationId; }
    public Instant getAnsweredAt() { return answeredAt; }
    public boolean isActive() { return active; }

    public void deactivate(Instant at) {
        this.active = false;
        this.answerStatus = FlowAnswerStatus.DEACTIVATED;
        this.deactivatedAt = at;
    }
}
