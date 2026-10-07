package com.momna.platform.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events", schema = "momna")
public class AuditEventEntity {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Column(name = "actor_ref", nullable = false)
    private String actorRef;

    @Column(name = "subject_ref", nullable = false)
    private String subjectRef;

    @Column(name = "source_module", nullable = false)
    private String sourceModule;

    @Column(name = "flow_instance_id")
    private String flowInstanceId;

    @Column(name = "context_snapshot_id")
    private String contextSnapshotId;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(name = "result_code", nullable = false)
    private String resultCode;

    @Column(name = "policy_version")
    private String policyVersion;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_category")
    private String errorCategory;

    @Column(name = "error_retryable")
    private Boolean errorRetryable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "versions_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> versionsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected AuditEventEntity() {}

    public AuditEventEntity(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String requestId,
        String traceId,
        String actorRef,
        String subjectRef,
        String sourceModule,
        String flowInstanceId,
        String contextSnapshotId,
        String actionCode,
        String resultCode,
        String policyVersion,
        String errorCode,
        String errorCategory,
        Boolean errorRetryable,
        Map<String, Object> versionsJson,
        Map<String, Object> metadataJson
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.occurredAt = occurredAt;
        this.requestId = requestId;
        this.traceId = traceId;
        this.actorRef = actorRef;
        this.subjectRef = subjectRef;
        this.sourceModule = sourceModule;
        this.flowInstanceId = flowInstanceId;
        this.contextSnapshotId = contextSnapshotId;
        this.actionCode = actionCode;
        this.resultCode = resultCode;
        this.policyVersion = policyVersion;
        this.errorCode = errorCode;
        this.errorCategory = errorCategory;
        this.errorRetryable = errorRetryable;
        this.versionsJson = versionsJson == null ? Map.of() : Map.copyOf(versionsJson);
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getTraceId() { return traceId; }
    public String getSubjectRef() { return subjectRef; }
}
