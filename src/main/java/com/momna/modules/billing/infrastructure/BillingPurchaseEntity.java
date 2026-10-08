package com.momna.modules.billing.infrastructure;

import com.momna.modules.billing.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "billing_purchase_transactions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "external_transaction_id"})
)
public class BillingPurchaseEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingEnvironment environment;

    @Column(name = "external_transaction_id", nullable = false)
    private String externalTransactionId;

    @Column(name = "original_transaction_id", nullable = false)
    private String originalTransactionId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseStatus status;

    @Column(name = "evidence_hash", nullable = false)
    private String evidenceHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BillingPurchaseEntity() {}

    public BillingPurchaseEntity(
        UUID id, UUID userId, BillingProvider provider, BillingEnvironment environment,
        String externalTransactionId, String originalTransactionId, String productId,
        Instant purchasedAt, Instant verifiedAt, PurchaseStatus status, String evidenceHash,
        Instant createdAt, Instant updatedAt
    ) {
        this.id = id; this.userId = userId; this.provider = provider; this.environment = environment;
        this.externalTransactionId = externalTransactionId; this.originalTransactionId = originalTransactionId;
        this.productId = productId; this.purchasedAt = purchasedAt; this.verifiedAt = verifiedAt;
        this.status = status; this.evidenceHash = evidenceHash; this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public BillingProvider getProvider() { return provider; }
    public String getOriginalTransactionId() { return originalTransactionId; }
    public String getExternalTransactionId() { return externalTransactionId; }
    public String getProductId() { return productId; }
    public Instant getPurchasedAt() { return purchasedAt; }
    public PurchaseStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
