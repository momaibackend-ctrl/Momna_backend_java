package com.momna.modules.billing.application;

import com.momna.modules.billing.domain.*;
import java.time.Instant;
import java.util.List;

public interface BillingProviderAdapter {
    BillingProvider provider();

    ReconciliationResult reconcileUserPurchases(String userId);

    record VerifiedProviderPurchase(
        BillingProvider provider,
        BillingEnvironment environment,
        String externalProductId,
        String externalTransactionId,
        String originalTransactionId,
        Instant purchasedAt,
        boolean revoked,
        boolean refunded,
        String evidenceHash
    ) {}

    record ReconciliationResult(
        List<VerifiedProviderPurchase> verifiedPurchases,
        Instant completedAt
    ) {}
}
