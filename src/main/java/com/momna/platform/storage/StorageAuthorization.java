package com.momna.platform.storage;

public interface StorageAuthorization {
    boolean mayAccess(String principalId, ObjectMetadata metadata);
}
