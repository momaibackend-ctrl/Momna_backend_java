package com.momna.platform.storage;

import java.net.URI;
import java.time.Duration;

public interface PrivateObjectStorage {
    StoredObject put(
        String ownerId,
        String bucket,
        String key,
        byte[] bytes,
        String contentType,
        AssetSensitivity sensitivity
    );

    ObjectMetadata getMetadata(String bucket, String key);

    void delete(StoredObject objectRef);

    SignedUrl signedGetUrl(String principalId, StoredObject objectRef, Duration ttl);

    record SignedUrl(URI uri, Duration expiresIn) {}
}
