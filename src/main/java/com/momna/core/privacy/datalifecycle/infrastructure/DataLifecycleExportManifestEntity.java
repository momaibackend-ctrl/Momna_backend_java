package com.momna.core.privacy.datalifecycle.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_lifecycle_export_manifests", schema = "momna")
public class DataLifecycleExportManifestEntity {
    @Id
    @Column(name = "operation_id")
    private UUID operationId;
    @Column(name = "subject_user_id", nullable = false) private String subjectUserId;
    @Column(name = "snapshot_at", nullable = false) private Instant snapshotAt;
    @Column(name = "schema_version", nullable = false) private int schemaVersion;
    @Column(name = "included_owners", nullable = false) private String includedOwners;
    @Column(name = "included_resource_count", nullable = false) private int includedResourceCount;
    @Column(name = "excluded_resource_count", nullable = false) private int excludedResourceCount;
    @Column(name = "artifact_bucket", nullable = false) private String artifactBucket;
    @Column(name = "artifact_key", nullable = false) private String artifactKey;
    @Column(name = "artifact_content_type", nullable = false) private String artifactContentType;
    @Column(name = "artifact_size_bytes", nullable = false) private long artifactSizeBytes;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected DataLifecycleExportManifestEntity() {}

    public DataLifecycleExportManifestEntity(
        UUID operationId, String subjectUserId, Instant snapshotAt, int schemaVersion,
        String includedOwners, int includedResourceCount, int excludedResourceCount,
        String artifactBucket, String artifactKey, String artifactContentType,
        long artifactSizeBytes, Instant createdAt
    ) {
        this.operationId = operationId;
        this.subjectUserId = subjectUserId;
        this.snapshotAt = snapshotAt;
        this.schemaVersion = schemaVersion;
        this.includedOwners = includedOwners;
        this.includedResourceCount = includedResourceCount;
        this.excludedResourceCount = excludedResourceCount;
        this.artifactBucket = artifactBucket;
        this.artifactKey = artifactKey;
        this.artifactContentType = artifactContentType;
        this.artifactSizeBytes = artifactSizeBytes;
        this.createdAt = createdAt;
    }
}
