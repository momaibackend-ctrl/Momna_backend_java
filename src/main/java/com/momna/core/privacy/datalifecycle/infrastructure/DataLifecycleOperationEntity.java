package com.momna.core.privacy.datalifecycle.infrastructure;

import com.momna.core.privacy.PrivacyScope;
import com.momna.core.privacy.datalifecycle.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "data_lifecycle_operations", schema = "momna")
public class DataLifecycleOperationEntity {
    @Id
    @Column(name = "operation_id")
    private UUID operationId;

    @Column(name = "subject_user_id", nullable = false)
    private String subjectUserId;

    @Column(name = "actor_user_id", nullable = false)
    private String actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataLifecycleOperationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataLifecycleOperationState state;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "resource_owner")
    private String resourceOwner;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy_scope")
    private PrivacyScope privacyScope;

    @Column(name = "retention_anchor_at")
    private Instant retentionAnchorAt;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "timezone_at_event")
    private String timezoneAtEvent;

    @Column(name = "local_date_at_event")
    private LocalDate localDateAtEvent;

    @Column(name = "deletion_plan_id")
    private UUID deletionPlanId;

    @Column(name = "related_operation_id")
    private UUID relatedOperationId;

    @Column(name = "policy_key")
    private String policyKey;

    @Column(name = "policy_version")
    private Integer policyVersion;

    @Column(name = "artifact_bucket")
    private String artifactBucket;

    @Column(name = "artifact_key")
    private String artifactKey;

    @Column(name = "artifact_content_type")
    private String artifactContentType;

    @Column(name = "artifact_size_bytes")
    private Long artifactSizeBytes;

    @Column(name = "timezone_at_request")
    private String timezoneAtRequest;

    @Column(name = "local_date_at_request")
    private LocalDate localDateAtRequest;

    @Column(name = "error_code")
    private String errorCode;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DataLifecycleOperationEntity() {}

    public DataLifecycleOperationEntity(
        UUID operationId,
        String subjectUserId,
        String actorUserId,
        DataLifecycleOperationKind kind,
        String idempotencyKey,
        String traceId,
        Instant requestedAt
    ) {
        this.operationId = operationId;
        this.subjectUserId = subjectUserId;
        this.actorUserId = actorUserId;
        this.kind = kind;
        this.state = DataLifecycleOperationState.QUEUED;
        this.idempotencyKey = idempotencyKey;
        this.traceId = traceId;
        this.requestedAt = requestedAt;
    }

    public UUID getOperationId() { return operationId; }
    public String getSubjectUserId() { return subjectUserId; }
    public String getActorUserId() { return actorUserId; }
    public DataLifecycleOperationKind getKind() { return kind; }
    public DataLifecycleOperationState getState() { return state; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getTraceId() { return traceId; }
    public Instant getRequestedAt() { return requestedAt; }
    public String getResourceOwner() { return resourceOwner; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public PrivacyScope getPrivacyScope() { return privacyScope; }
    public Instant getRetentionAnchorAt() { return retentionAnchorAt; }
    public String getPolicyKey() { return policyKey; }
    public Integer getPolicyVersion() { return policyVersion; }
    public UUID getDeletionPlanId() { return deletionPlanId; }
    public UUID getRelatedOperationId() { return relatedOperationId; }
    public String getArtifactBucket() { return artifactBucket; }
    public String getArtifactKey() { return artifactKey; }
    public String getArtifactContentType() { return artifactContentType; }
    public Long getArtifactSizeBytes() { return artifactSizeBytes; }
    public String getErrorCode() { return errorCode; }
    public long getRowVersion() { return rowVersion; }

    public void attachResource(
        String owner,
        String resourceType,
        String resourceId,
        PrivacyScope privacyScope,
        Instant retentionAnchorAt,
        String policyKey,
        int policyVersion
    ) {
        this.resourceOwner = owner;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.privacyScope = privacyScope;
        this.retentionAnchorAt = retentionAnchorAt;
        this.policyKey = policyKey;
        this.policyVersion = policyVersion;
    }

    public void attachArtifact(String bucket, String key, String contentType, long sizeBytes) {
        this.artifactBucket = bucket;
        this.artifactKey = key;
        this.artifactContentType = contentType;
        this.artifactSizeBytes = sizeBytes;
    }

    public void captureRequestLocalTime(String timezone, LocalDate localDate) {
        this.timezoneAtRequest = timezone;
        this.localDateAtRequest = localDate;
    }

    public void markRunning() {
        this.state = DataLifecycleOperationState.RUNNING;
        this.errorCode = null;
    }

    public void markSucceeded() {
        this.state = DataLifecycleOperationState.SUCCEEDED;
        this.errorCode = null;
    }

    public void markFailed(String errorCode) {
        this.state = DataLifecycleOperationState.FAILED;
        this.errorCode = errorCode;
    }
}
