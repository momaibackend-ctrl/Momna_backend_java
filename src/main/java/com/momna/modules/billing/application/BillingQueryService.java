package com.momna.modules.billing.application;

import com.momna.modules.billing.domain.EntitlementStatus;
import com.momna.modules.billing.infrastructure.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BillingQueryService {
    private static final Duration MAX_VERIFIED_STATE_AGE = Duration.ofHours(48);

    private final BillingSubscriptionRepository subscriptions;
    private final BillingEntitlementRepository entitlements;
    private final Clock clock = Clock.systemUTC();

    public BillingQueryService(
        BillingSubscriptionRepository subscriptions,
        BillingEntitlementRepository entitlements
    ) {
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
    }

    public List<EntitlementView> entitlements(String userId) {
        var id = parseUserId(userId);
        return entitlements.findByUserIdOrderByEntitlementCodeAsc(id).stream()
            .map(this::boundedStale)
            .toList();
    }

    public boolean hasEntitlement(String userId, String entitlementCode) {
        return entitlements(userId).stream().anyMatch(x ->
            x.entitlementCode().equals(entitlementCode)
                && (x.status().equals("ACTIVE") || x.status().equals("GRACE"))
        );
    }

    public SubscriptionSummary subscription(String userId) {
        var subscription = subscriptions.findFirstByUserIdOrderByCurrentPeriodEndDesc(parseUserId(userId))
            .orElse(null);

        if (subscription == null) {
            return new SubscriptionSummary(
                null, null, null, null, null, false, null, Set.of("RESTORE")
            );
        }

        var renewsAt = Boolean.TRUE.equals(subscription.getAutoRenewEnabled())
            && !subscription.isCancelAtPeriodEnd()
            ? subscription.getCurrentPeriodEnd()
            : null;

        var actions = new java.util.HashSet<String>();
        actions.add("MANAGE_EXTERNALLY");
        actions.add("RESTORE");
        if (subscription.getStatus().name().equals("NEEDS_RECONCILIATION")) {
            actions.add("RETRY_SYNC");
        }

        return new SubscriptionSummary(
            subscription.getProductId(),
            subscription.getStatus().name(),
            subscription.getCurrentPeriodStart(),
            subscription.getCurrentPeriodEnd(),
            renewsAt,
            subscription.isCancelAtPeriodEnd(),
            subscription.getProvider().name(),
            Set.copyOf(actions)
        );
    }

    private EntitlementView boundedStale(BillingEntitlementEntity entity) {
        var status = entity.getStatus();
        var reasonCode = entity.getReasonCode();
        var resolvedAt = entity.getResolvedAt();
        var now = clock.instant();

        if ((status == EntitlementStatus.ACTIVE || status == EntitlementStatus.GRACE)
            && entity.getValidUntil() != null
            && entity.getValidUntil().plus(MAX_VERIFIED_STATE_AGE).isBefore(now)) {
            status = EntitlementStatus.TEMPORARILY_UNAVAILABLE;
            reasonCode = "STALE_REQUIRES_RECONCILIATION";
            resolvedAt = now;
        }

        return new EntitlementView(
            entity.getEntitlementCode(),
            status.name(),
            entity.getValidFrom(),
            entity.getValidUntil(),
            entity.getSourceType().name(),
            reasonCode,
            resolvedAt
        );
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid authenticated user id");
        }
    }

    public record EntitlementView(
        String entitlementCode,
        String status,
        Instant validFrom,
        Instant validUntil,
        String sourceType,
        String reasonCode,
        Instant resolvedAt
    ) {}

    public record SubscriptionSummary(
        String planKey,
        String status,
        Instant billingPeriodStart,
        Instant billingPeriodEnd,
        Instant renewsAt,
        boolean cancelAtPeriodEnd,
        String provider,
        Set<String> actions
    ) {}
}
