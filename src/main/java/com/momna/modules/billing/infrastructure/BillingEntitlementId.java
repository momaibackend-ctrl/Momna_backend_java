package com.momna.modules.billing.infrastructure;

import java.io.Serializable;
import java.util.UUID;

public class BillingEntitlementId implements Serializable {
    public UUID userId;
    public String entitlementCode;

    public BillingEntitlementId() {}
    public BillingEntitlementId(UUID userId, String entitlementCode) {
        this.userId = userId;
        this.entitlementCode = entitlementCode;
    }
}
