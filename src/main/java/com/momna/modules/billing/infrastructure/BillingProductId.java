package com.momna.modules.billing.infrastructure;

import com.momna.modules.billing.domain.BillingProvider;
import java.io.Serializable;

public class BillingProductId implements Serializable {
    public BillingProvider provider;
    public String externalProductId;
    public long metadataVersion;
    public BillingProductId() {}
}
