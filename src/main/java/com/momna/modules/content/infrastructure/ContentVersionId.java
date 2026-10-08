package com.momna.modules.content.infrastructure;

import java.io.Serializable;

public class ContentVersionId implements Serializable {
    public String contentId;
    public int version;

    public ContentVersionId() {}
    public ContentVersionId(String contentId, int version) {
        this.contentId = contentId;
        this.version = version;
    }
}
