package com.momna.modules.flow.infrastructure;

import java.io.Serializable;

public class FlowDefinitionId implements Serializable {
    public String definitionKey;
    public int definitionVersion;

    public FlowDefinitionId() {}
    public FlowDefinitionId(String definitionKey, int definitionVersion) {
        this.definitionKey = definitionKey;
        this.definitionVersion = definitionVersion;
    }
}
