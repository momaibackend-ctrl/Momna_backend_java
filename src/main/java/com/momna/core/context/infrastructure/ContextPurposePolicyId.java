package com.momna.core.context.infrastructure;

import java.io.Serializable;

public class ContextPurposePolicyId implements Serializable {
    public String purposeKey;
    public int purposeVersion;

    public ContextPurposePolicyId() {}
    public ContextPurposePolicyId(String purposeKey, int purposeVersion) {
        this.purposeKey = purposeKey;
        this.purposeVersion = purposeVersion;
    }
}
