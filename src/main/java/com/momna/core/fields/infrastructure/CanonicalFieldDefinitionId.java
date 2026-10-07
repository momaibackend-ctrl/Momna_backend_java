package com.momna.core.fields.infrastructure;

import java.io.Serializable;

public class CanonicalFieldDefinitionId implements Serializable {
    public String fieldId;
    public int definitionVersion;

    public CanonicalFieldDefinitionId() {}
    public CanonicalFieldDefinitionId(String fieldId, int definitionVersion) {
        this.fieldId = fieldId;
        this.definitionVersion = definitionVersion;
    }
}
