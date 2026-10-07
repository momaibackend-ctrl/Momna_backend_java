package com.momna.modules.billing.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingSubscriptionRepository
    extends JpaRepository<BillingSubscriptionEntity, BillingSubscriptionId> {

    Optional<BillingSubscriptionEntity> findFirstByUserIdOrderByCurrentPeriodEndDesc(UUID userId);
}
