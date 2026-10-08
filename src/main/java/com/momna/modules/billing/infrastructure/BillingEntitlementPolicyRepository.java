package com.momna.modules.billing.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEntitlementPolicyRepository
    extends JpaRepository<BillingEntitlementPolicyEntity, String> {}
