package com.momna.platform.storage.infrastructure;

import com.momna.platform.storage.AssetSensitivity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "platform_object_metadata", schema = "momna")
@IdClass(ObjectMetadataId.class)
public class ObjectMetadataEntity {
    @Id
    private String bucket;

    @Id
    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetSensitivity sensitivity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ObjectMetadataEntity() {}

    public ObjectMetadataEntity(
        String bucket,
        String objectKey,
        String ownerId,
        String contentType,
        long sizeBytes,
        AssetSensitivity sensitivity,
        Instant createdAt
    ) {
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.ownerId = ownerId;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sensitivity = sensitivity;
        this.createdAt = createdAt;
    }

    public String getBucket() { return bucket; }
    public String getObjectKey() { return objectKey; }
    public String getOwnerId() { return ownerId; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public AssetSensitivity getSensitivity() { return sensitivity; }
    public Instant getCreatedAt() { return createdAt; }
}
