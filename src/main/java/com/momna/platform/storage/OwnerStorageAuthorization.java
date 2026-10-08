package com.momna.platform.storage;

import org.springframework.stereotype.Component;

@Component
public class OwnerStorageAuthorization implements StorageAuthorization {
    @Override
    public boolean mayAccess(String principalId, ObjectMetadata metadata) {
        return metadata != null && metadata.ownerId().equals(principalId);
    }
}
