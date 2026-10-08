package com.momna.modules.billing.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEntitlementRepository
    extends JpaRepository<BillingEntitlementEntity, BillingEntitlementId> {

    List<BillingEntitlementEntity> findByUserIdOrderByEntitlementCodeAsc(UUID userId);
}
