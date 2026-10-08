package com.momna.core.privacy.datalifecycle.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_lifecycle_archive_manifests", schema = "momna")
public class DataLifecycleArchiveManifestEntity {
    @Id
    @Column(name = "operation_id")
    private UUID operationId;
    @Column(name = "policy_key", nullable = false) private String policyKey;
    @Column(name = "policy_version", nullable = false) private int policyVersion;
    @Column(name = "artifact_bucket", nullable = false) private String artifactBucket;
    @Column(name = "artifact_key", nullable = false) private String artifactKey;
    @Column(name = "artifact_content_type", nullable = false) private String artifactContentType;
    @Column(name = "artifact_size_bytes", nullable = false) private long artifactSizeBytes;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected DataLifecycleArchiveManifestEntity() {}

    public UUID getOperationId() { return operationId; }
    public String getPolicyKey() { return policyKey; }
    public int getPolicyVersion() { return policyVersion; }
    public String getArtifactBucket() { return artifactBucket; }
    public String getArtifactKey() { return artifactKey; }
    public String getArtifactContentType() { return artifactContentType; }
    public long getArtifactSizeBytes() { return artifactSizeBytes; }
    public Instant getCreatedAt() { return createdAt; }

    public DataLifecycleArchiveManifestEntity(
        UUID operationId, String policyKey, int policyVersion,
        String artifactBucket, String artifactKey, String artifactContentType,
        long artifactSizeBytes, Instant createdAt
    ) {
        this.operationId = operationId;
        this.policyKey = policyKey;
        this.policyVersion = policyVersion;
        this.artifactBucket = artifactBucket;
        this.artifactKey = artifactKey;
        this.artifactContentType = artifactContentType;
        this.artifactSizeBytes = artifactSizeBytes;
        this.createdAt = createdAt;
    }
}
