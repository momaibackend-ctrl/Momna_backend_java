package com.momna.platform.storage.infrastructure;

import java.io.Serializable;

public class ObjectMetadataId implements Serializable {
    public String bucket;
    public String objectKey;

    public ObjectMetadataId() {}

    public ObjectMetadataId(String bucket, String objectKey) {
        this.bucket = bucket;
        this.objectKey = objectKey;
    }
}
