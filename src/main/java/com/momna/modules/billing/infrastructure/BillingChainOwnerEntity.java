package com.momna.modules.billing.infrastructure;

import com.momna.modules.billing.domain.BillingProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_provider_chain_owners")
@IdClass(BillingChainOwnerId.class)
public class BillingChainOwnerEntity {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingProvider provider;

    @Id
    @Column(name = "original_transaction_id", nullable = false)
    private String originalTransactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    protected BillingChainOwnerEntity() {}

    public BillingChainOwnerEntity(BillingProvider provider, String originalTransactionId, UUID userId, Instant claimedAt) {
        this.provider = provider; this.originalTransactionId = originalTransactionId;
        this.userId = userId; this.claimedAt = claimedAt;
    }

    public UUID getUserId() { return userId; }
}
