package com.momna.modules.billing.infrastructure;

import com.momna.modules.billing.domain.BillingProvider;
import com.momna.modules.billing.domain.SubscriptionStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_subscriptions")
@IdClass(BillingSubscriptionId.class)
public class BillingSubscriptionEntity {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingProvider provider;

    @Id
    @Column(name = "original_transaction_id", nullable = false)
    private String originalTransactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "auto_renew_enabled")
    private Boolean autoRenewEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    @Column(name = "source_of_truth_version", nullable = false)
    private long sourceOfTruthVersion;

    protected BillingSubscriptionEntity() {}

    public BillingProvider getProvider() { return provider; }
    public String getOriginalTransactionId() { return originalTransactionId; }
    public UUID getUserId() { return userId; }
    public String getProductId() { return productId; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public Boolean getAutoRenewEnabled() { return autoRenewEnabled; }
    public SubscriptionStatus getStatus() { return status; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public Instant getLastVerifiedAt() { return lastVerifiedAt; }
}
