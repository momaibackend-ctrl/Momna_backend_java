package com.momna.core.privacy.datalifecycle.infrastructure;

import java.io.Serializable;

public class DataLifecyclePolicyId implements Serializable {
    public String policyKey;
    public int policyVersion;
    public DataLifecyclePolicyId() {}
    public DataLifecyclePolicyId(String policyKey, int policyVersion) {
        this.policyKey = policyKey;
        this.policyVersion = policyVersion;
    }
}
