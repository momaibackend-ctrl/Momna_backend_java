package com.momna.modules.billing.infrastructure;

import jakarta.persistence.*;

@Entity
@Table(name = "billing_entitlement_policies")
public class BillingEntitlementPolicyEntity {
    @Id
    @Column(name = "policy_id")
    private String policyId;

    @Column(name = "entitlement_code", nullable = false)
    private String entitlementCode;

    @Column(nullable = false)
    private long version;

    @Column(name = "grace_allowed", nullable = false)
    private boolean graceAllowed;

    protected BillingEntitlementPolicyEntity() {}

    public String getPolicyId() { return policyId; }
    public String getEntitlementCode() { return entitlementCode; }
    public long getVersion() { return version; }
    public boolean isGraceAllowed() { return graceAllowed; }
}
