package com.momna.core.context.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "context_snapshots",
    schema = "momna",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_id", "purpose_key", "purpose_version", "operation_id"}
    )
)
public class ContextSnapshotEntity {
    @Id
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "purpose_key", nullable = false)
    private String purposeKey;

    @Column(name = "purpose_version", nullable = false)
    private int purposeVersion;

    @Column(name = "policy_schema_version", nullable = false)
    private int policySchemaVersion;

    @Column(name = "context_schema_version", nullable = false)
    private int contextSchemaVersion;

    @Column(name = "output_schema_version", nullable = false)
    private int outputSchemaVersion;

    @Column(name = "operation_id", nullable = false)
    private String operationId;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "content_fingerprint", nullable = false, length = 64)
    private String contentFingerprint;

    @Column(name = "reference_at", nullable = false)
    private Instant referenceAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "snapshot_version", nullable = false)
    private long snapshotVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshotJson;

    protected ContextSnapshotEntity() {}

    public ContextSnapshotEntity(
        UUID snapshotId,
        String userId,
        String purposeKey,
        int purposeVersion,
        int policySchemaVersion,
        int contextSchemaVersion,
        int outputSchemaVersion,
        String operationId,
        String requestFingerprint,
        String contentFingerprint,
        Instant referenceAt,
        Instant createdAt,
        Map<String, Object> snapshotJson
    ) {
        this.snapshotId = snapshotId;
        this.userId = userId;
        this.purposeKey = purposeKey;
        this.purposeVersion = purposeVersion;
        this.policySchemaVersion = policySchemaVersion;
        this.contextSchemaVersion = contextSchemaVersion;
        this.outputSchemaVersion = outputSchemaVersion;
        this.operationId = operationId;
        this.requestFingerprint = requestFingerprint;
        this.contentFingerprint = contentFingerprint;
        this.referenceAt = referenceAt;
        this.createdAt = createdAt;
        this.snapshotVersion = 1L;
        this.snapshotJson = snapshotJson;
    }

    public UUID getSnapshotId() { return snapshotId; }
    public String getUserId() { return userId; }
    public String getPurposeKey() { return purposeKey; }
    public int getPurposeVersion() { return purposeVersion; }
    public int getPolicySchemaVersion() { return policySchemaVersion; }
    public int getContextSchemaVersion() { return contextSchemaVersion; }
    public int getOutputSchemaVersion() { return outputSchemaVersion; }
    public String getOperationId() { return operationId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getContentFingerprint() { return contentFingerprint; }
    public Instant getReferenceAt() { return referenceAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getSnapshotVersion() { return snapshotVersion; }
    public Map<String, Object> getSnapshotJson() { return snapshotJson; }
}
