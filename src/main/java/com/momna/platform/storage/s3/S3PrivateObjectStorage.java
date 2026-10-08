package com.momna.platform.storage.s3;

import com.momna.platform.storage.*;
import com.momna.platform.storage.infrastructure.*;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class S3PrivateObjectStorage implements PrivateObjectStorage {
    private static final long MAX_OBJECT_BYTES = 25L * 1024 * 1024;
    private static final Duration MAX_SIGNED_URL_TTL = Duration.ofMinutes(15);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageAuthorization authorization;
    private final ObjectMetadataRepository metadata;
    private final Set<String> allowedBuckets;

    public S3PrivateObjectStorage(
        S3Client s3,
        S3Presigner presigner,
        StorageAuthorization authorization,
        ObjectMetadataRepository metadata,
        Set<String> allowedBuckets
    ) {
        this.s3 = s3;
        this.presigner = presigner;
        this.authorization = authorization;
        this.metadata = metadata;
        this.allowedBuckets = Set.copyOf(allowedBuckets);
    }

    @Override
    public StoredObject put(
        String ownerId,
        String bucket,
        String key,
        byte[] bytes,
        String contentType,
        AssetSensitivity sensitivity
    ) {
        validateOwner(ownerId);
        validateLocation(bucket, key);
        if (bytes == null || bytes.length > MAX_OBJECT_BYTES) {
            throw new StorageException("SIZE_LIMIT", "Object exceeds storage size policy");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new StorageException("UNSUPPORTED_CONTENT_TYPE", "Content type is required");
        }

        try {
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromBytes(bytes)
            );
        } catch (S3Exception failure) {
            throw new StorageException("PROVIDER_UNAVAILABLE", "Object storage provider unavailable", failure);
        }

        var stored = new StoredObject(bucket, key, contentType, bytes.length, sensitivity);
        metadata.save(new ObjectMetadataEntity(
            bucket,
            key,
            ownerId,
            contentType,
            bytes.length,
            sensitivity,
            Instant.now()
        ));
        return stored;
    }

    @Override
    public ObjectMetadata getMetadata(String bucket, String key) {
        validateLocation(bucket, key);
        return metadata.findById(new ObjectMetadataId(bucket, key))
            .map(this::toMetadata)
            .orElse(null);
    }

    @Override
    public void delete(StoredObject objectRef) {
        validateLocation(objectRef.bucket(), objectRef.key());
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(objectRef.bucket())
                .key(objectRef.key())
                .build());
        } catch (S3Exception failure) {
            throw new StorageException("PROVIDER_UNAVAILABLE", "Object storage provider unavailable", failure);
        }
        metadata.deleteById(new ObjectMetadataId(objectRef.bucket(), objectRef.key()));
    }

    @Override
    public SignedUrl signedGetUrl(
        String principalId,
        StoredObject objectRef,
        Duration ttl
    ) {
        validateLocation(objectRef.bucket(), objectRef.key());
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_SIGNED_URL_TTL) > 0) {
            throw new IllegalArgumentException("Signed URL TTL exceeds policy");
        }

        var meta = getMetadata(objectRef.bucket(), objectRef.key());
        if (meta == null) throw new StorageException("NOT_FOUND", "Object metadata not found");
        if (!authorization.mayAccess(principalId, meta)) {
            throw new StorageException("FORBIDDEN", "Storage access denied");
        }

        try {
            var request = GetObjectRequest.builder()
                .bucket(objectRef.bucket())
                .key(objectRef.key())
                .build();
            var signed = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(request)
                    .build()
            );
            return new SignedUrl(URI.create(signed.url().toString()), ttl);
        } catch (RuntimeException failure) {
            throw new StorageException("PROVIDER_UNAVAILABLE", "Unable to create signed object URL", failure);
        }
    }

    private ObjectMetadata toMetadata(ObjectMetadataEntity entity) {
        return new ObjectMetadata(
            new StoredObject(
                entity.getBucket(),
                entity.getObjectKey(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getSensitivity()
            ),
            entity.getOwnerId(),
            entity.getCreatedAt()
        );
    }

    private void validateOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId is required");
        }
    }

    private void validateLocation(String bucket, String key) {
        if (bucket == null || bucket.isBlank() || bucket.toLowerCase().contains("public")) {
            throw new IllegalArgumentException("Sensitive object bucket must be private");
        }
        if (!allowedBuckets.isEmpty() && !allowedBuckets.contains(bucket)) {
            throw new StorageException("FORBIDDEN", "Bucket is not configured for Momna");
        }
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")) {
            throw new IllegalArgumentException("Invalid object key");
        }
    }
}
