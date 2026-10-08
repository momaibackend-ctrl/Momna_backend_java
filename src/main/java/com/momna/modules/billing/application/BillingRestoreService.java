package com.momna.modules.billing.application;

import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.billing.domain.*;
import com.momna.modules.billing.infrastructure.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingRestoreService {
    private static final Duration MAX_VERIFIED_STATE_AGE = Duration.ofHours(48);

    private final Map<BillingProvider, BillingProviderAdapter> adapters;
    private final BillingProductRepository products;
    private final BillingEntitlementPolicyRepository policies;
    private final BillingPurchaseRepository purchases;
    private final BillingChainOwnerRepository chainOwners;
    private final BillingSubscriptionRepository subscriptions;
    private final BillingEntitlementRepository entitlements;
    private final Clock clock = Clock.systemUTC();

    public BillingRestoreService(
        ObjectProvider<BillingProviderAdapter> adapters,
        BillingProductRepository products,
        BillingEntitlementPolicyRepository policies,
        BillingPurchaseRepository purchases,
        BillingChainOwnerRepository chainOwners,
        BillingSubscriptionRepository subscriptions,
        BillingEntitlementRepository entitlements
    ) {
        this.adapters = adapters.orderedStream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(BillingProviderAdapter::provider, x -> x)
        );
        this.products = products;
        this.policies = policies;
        this.purchases = purchases;
        this.chainOwners = chainOwners;
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
    }

    @Transactional
    public List<BillingEntitlementEntity> restore(AuthenticatedActor actor, BillingProvider provider) {
        var adapter = adapters.get(provider);
        if (adapter == null) {
            throw new BillingException("PROVIDER_NOT_CONFIGURED", "Billing provider adapter unavailable");
        }

        var result = adapter.reconcileUserPurchases(actor.userId());
        var userId = parseUserId(actor.userId());
        var verified = new ArrayList<>(result.verifiedPurchases());
        verified.sort(Comparator.comparing(BillingProviderAdapter.VerifiedProviderPurchase::purchasedAt));

        for (var purchase : verified) {
            if (purchase.provider() != provider) continue;

            var product = activeProduct(provider, purchase.externalProductId(), result.completedAt());
            if (product == null) continue;

            var chainId = new BillingChainOwnerId(provider, purchase.originalTransactionId());
            var owner = chainOwners.findById(chainId).orElse(null);
            if (owner != null && !owner.getUserId().equals(userId)) continue;
            if (owner == null) {
                chainOwners.save(new BillingChainOwnerEntity(
                    provider, purchase.originalTransactionId(), userId, result.completedAt()
                ));
            }

            var existing = purchases.findByProviderAndExternalTransactionId(
                provider, purchase.externalTransactionId()
            ).orElse(null);
            if (existing != null && !existing.getUserId().equals(userId)) continue;

            var status = purchase.revoked()
                ? PurchaseStatus.REVOKED
                : purchase.refunded() ? PurchaseStatus.REFUNDED : PurchaseStatus.VERIFIED;

            var persisted = new BillingPurchaseEntity(
                existing == null ? UUID.randomUUID() : existing.getId(),
                userId,
                provider,
                purchase.environment(),
                purchase.externalTransactionId(),
                purchase.originalTransactionId(),
                product.getInternalProductId(),
                purchase.purchasedAt(),
                result.completedAt(),
                status,
                purchase.evidenceHash(),
                existing == null ? result.completedAt() : existing.getCreatedAt(),
                result.completedAt()
            );
            purchases.save(persisted);

            BillingSubscriptionEntity subscription = null;
            if (purchase.subscription() != null) {
                var s = purchase.subscription();
                subscription = new BillingSubscriptionEntity(
                    provider,
                    s.originalTransactionId(),
                    userId,
                    product.getInternalProductId(),
                    s.startedAt(),
                    s.currentPeriodStart(),
                    s.currentPeriodEnd(),
                    s.autoRenewEnabled(),
                    s.status(),
                    s.cancelAtPeriodEnd(),
                    s.cancelledAt(),
                    result.completedAt(),
                    s.sourceOfTruthVersion()
                );
                subscriptions.save(subscription);
            }

            resolveEntitlement(userId, product, persisted, subscription, result.completedAt());
        }

        return entitlements.findByUserIdOrderByEntitlementCodeAsc(userId);
    }

    private void resolveEntitlement(
        UUID userId,
        BillingProductEntity product,
        BillingPurchaseEntity purchase,
        BillingSubscriptionEntity subscription,
        Instant now
    ) {
        var policy = policies.findById(product.getEntitlementPolicyId())
            .orElseThrow(() -> new BillingException(
                "ENTITLEMENT_POLICY_MISSING", "Entitlement policy unavailable"
            ));

        EntitlementStatus status;
        if (purchase.getStatus() == PurchaseStatus.REVOKED || purchase.getStatus() == PurchaseStatus.REFUNDED) {
            status = EntitlementStatus.INACTIVE;
        } else if (product.getProductType() == BillingProductType.ONE_TIME
            && purchase.getStatus() == PurchaseStatus.VERIFIED) {
            status = EntitlementStatus.ACTIVE;
        } else if (subscription == null) {
            status = EntitlementStatus.TEMPORARILY_UNAVAILABLE;
        } else if (subscription.getStatus() == SubscriptionStatus.GRACE_PERIOD && policy.isGraceAllowed()) {
            status = EntitlementStatus.GRACE;
        } else if (Set.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.TRIAL,
            SubscriptionStatus.CANCELLED_ACTIVE_UNTIL_END
        ).contains(subscription.getStatus()) && subscription.getCurrentPeriodEnd().isAfter(now)) {
            status = EntitlementStatus.ACTIVE;
        } else if (Set.of(
            SubscriptionStatus.NEEDS_RECONCILIATION,
            SubscriptionStatus.BILLING_RETRY
        ).contains(subscription.getStatus())
            && subscription.getLastVerifiedAt().plus(MAX_VERIFIED_STATE_AGE).isAfter(now)) {
            status = EntitlementStatus.GRACE;
        } else {
            status = EntitlementStatus.INACTIVE;
        }

        var sourceType = product.getProductType() == BillingProductType.ONE_TIME
            ? EntitlementSourceType.ONE_TIME_PURCHASE
            : EntitlementSourceType.SUBSCRIPTION;

        entitlements.save(new BillingEntitlementEntity(
            userId,
            policy.getEntitlementCode(),
            status,
            purchase.getPurchasedAt(),
            subscription == null ? null : subscription.getCurrentPeriodEnd(),
            sourceType,
            purchase.getOriginalTransactionId(),
            "RESOLVED_FROM_VERIFIED_BILLING_STATE",
            now
        ));
    }

    private BillingProductEntity activeProduct(
        BillingProvider provider,
        String externalProductId,
        Instant at
    ) {
        return products.findByProviderAndExternalProductIdOrderByMetadataVersionDesc(provider, externalProductId)
            .stream()
            .filter(BillingProductEntity::isEnabled)
            .filter(x -> !x.getEffectiveFrom().isAfter(at))
            .filter(x -> x.getEffectiveTo() == null || !x.getEffectiveTo().isBefore(at))
            .findFirst()
            .orElse(null);
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException failure) {
            throw new BillingException("AUTH_INVALID", "Invalid authenticated user id");
        }
    }

    public static class BillingException extends RuntimeException {
        private final String code;
        public BillingException(String code, String message) {
            super(message);
            this.code = code;
        }
        public String code() { return code; }
    }
}
