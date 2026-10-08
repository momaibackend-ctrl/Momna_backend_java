package com.momna.modules.billing.infrastructure;

import com.momna.modules.billing.domain.BillingProvider;
import java.io.Serializable;

public class BillingSubscriptionId implements Serializable {
    public BillingProvider provider;
    public String originalTransactionId;

    public BillingSubscriptionId() {}
    public BillingSubscriptionId(BillingProvider provider, String originalTransactionId) {
        this.provider = provider;
        this.originalTransactionId = originalTransactionId;
    }
}
