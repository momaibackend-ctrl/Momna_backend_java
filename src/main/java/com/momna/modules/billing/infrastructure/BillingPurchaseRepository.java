package com.momna.modules.billing.infrastructure;

import com.momna.modules.billing.domain.BillingProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPurchaseRepository extends JpaRepository<BillingPurchaseEntity, UUID> {
    Optional<BillingPurchaseEntity> findByProviderAndExternalTransactionId(
        BillingProvider provider, String externalTransactionId
    );
    Optional<BillingPurchaseEntity> findFirstByProviderAndOriginalTransactionIdOrderByPurchasedAtDesc(
        BillingProvider provider, String originalTransactionId
    );
}
