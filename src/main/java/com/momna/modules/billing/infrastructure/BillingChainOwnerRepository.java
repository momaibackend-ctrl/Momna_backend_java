package com.momna.modules.billing.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingChainOwnerRepository
    extends JpaRepository<BillingChainOwnerEntity, BillingChainOwnerId> {}
