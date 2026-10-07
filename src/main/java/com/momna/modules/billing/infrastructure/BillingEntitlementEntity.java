package com.momna.modules.billing.infrastructure;

import com.momna.modules.billing.domain.EntitlementSourceType;
import com.momna.modules.billing.domain.EntitlementStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_entitlements")
@IdClass(BillingEntitlementId.class)
public class BillingEntitlementEntity {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "entitlement_code", nullable = false)
    private String entitlementCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntitlementStatus status;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private EntitlementSourceType sourceType;

    @Column(name = "source_ref", nullable = false)
    private String sourceRef;

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    @Column(name = "resolved_at", nullable = false)
    private Instant resolvedAt;

    protected BillingEntitlementEntity() {}

    public UUID getUserId() { return userId; }
    public String getEntitlementCode() { return entitlementCode; }
    public EntitlementStatus getStatus() { return status; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public EntitlementSourceType getSourceType() { return sourceType; }
    public String getSourceRef() { return sourceRef; }
    public String getReasonCode() { return reasonCode; }
    public Instant getResolvedAt() { return resolvedAt; }
}
