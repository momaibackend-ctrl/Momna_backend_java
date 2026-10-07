package com.momna.platform.storage;

import java.time.Instant;

public record ObjectMetadata(
    StoredObject objectRef,
    String ownerId,
    Instant createdAt
) {}
