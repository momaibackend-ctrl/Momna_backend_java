package com.momna.platform.storage;

public record StoredObject(
    String bucket,
    String key,
    String contentType,
    long sizeBytes,
    AssetSensitivity sensitivity
) {}
