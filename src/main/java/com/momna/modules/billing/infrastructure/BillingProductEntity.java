package com.momna.modules.billing.infrastructure;

import com.momna.modules.billing.domain.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "billing_products")
@IdClass(BillingProductId.class)
public class BillingProductEntity {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingProvider provider;

    @Id
    @Column(name = "external_product_id", nullable = false)
    private String externalProductId;

    @Id
    @Column(name = "metadata_version", nullable = false)
    private long metadataVersion;

    @Column(name = "internal_product_id", nullable = false)
    private String internalProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private BillingProductType productType;

    @Column(name = "entitlement_policy_id", nullable = false)
    private String entitlementPolicyId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    protected BillingProductEntity() {}

    public BillingProvider getProvider() { return provider; }
    public String getExternalProductId() { return externalProductId; }
    public long getMetadataVersion() { return metadataVersion; }
    public String getInternalProductId() { return internalProductId; }
    public BillingProductType getProductType() { return productType; }
    public String getEntitlementPolicyId() { return entitlementPolicyId; }
    public boolean isEnabled() { return enabled; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
}
