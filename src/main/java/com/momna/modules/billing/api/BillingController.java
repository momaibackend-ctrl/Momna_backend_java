package com.momna.modules.billing.api;

import com.momna.modules.auth.application.AuthException;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.billing.application.BillingQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class BillingController {
    private final BillingQueryService billing;

    public BillingController(BillingQueryService billing) {
        this.billing = billing;
    }

    @GetMapping("/entitlements")
    public EntitlementList entitlements(Authentication authentication) {
        var items = billing.entitlements(actor(authentication).userId()).stream()
            .map(x -> new EntitlementResponse(
                x.entitlementCode(), x.status(), x.validFrom(), x.validUntil(),
                x.sourceType(), x.reasonCode(), x.resolvedAt()
            ))
            .toList();
        return new EntitlementList(items);
    }

    @GetMapping("/subscription")
    public SubscriptionResponse subscription(Authentication authentication) {
        var value = billing.subscription(actor(authentication).userId());
        return new SubscriptionResponse(
            value.planKey(), value.status(), value.billingPeriodStart(), value.billingPeriodEnd(),
            value.renewsAt(), value.cancelAtPeriodEnd(), value.provider(), value.actions()
        );
    }

    private AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new AuthException("AUTH_REQUIRED", "Authentication required");
        }
        return actor;
    }

    public record EntitlementList(List<EntitlementResponse> items) {}

    public record EntitlementResponse(
        String entitlementCode,
        String status,
        Instant validFrom,
        Instant validUntil,
        String sourceType,
        String reasonCode,
        Instant resolvedAt
    ) {}

    public record SubscriptionResponse(
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
