package com.momna.modules.billing.infrastructure;

import com.momna.modules.billing.domain.BillingProvider;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingProductRepository extends JpaRepository<BillingProductEntity, BillingProductId> {
    List<BillingProductEntity> findByProviderAndExternalProductIdOrderByMetadataVersionDesc(
        BillingProvider provider, String externalProductId
    );
}
