package com.momna.modules.billing.infrastructure;

import com.momna.modules.billing.domain.BillingProvider;
import java.io.Serializable;

public class BillingChainOwnerId implements Serializable {
    public BillingProvider provider;
    public String originalTransactionId;
    public BillingChainOwnerId() {}
    public BillingChainOwnerId(BillingProvider provider, String originalTransactionId) {
        this.provider = provider; this.originalTransactionId = originalTransactionId;
    }
}
