package com.momna.foundation.api;

import com.momna.modules.auth.application.AuthApplicationService;
import com.momna.modules.auth.application.AuthException;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.auth.infrastructure.AuthSessionEntity;
import com.momna.modules.auth.infrastructure.LoginIdentityEntity;
import com.momna.modules.billing.application.BillingQueryService;
import com.momna.modules.lifecycle.application.LifecycleQueryService;
import com.momna.modules.lifecycle.domain.LifecycleContext;
import com.momna.modules.lifecycle.domain.LifecycleEntry;
import com.momna.modules.profile.application.ProfileService;
import com.momna.modules.profile.infrastructure.UserProfileEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountCenterController {
    private final AuthApplicationService auth;
    private final ProfileService profiles;
    private final LifecycleQueryService lifecycle;
    private final BillingQueryService billing;

    public AccountCenterController(
        AuthApplicationService auth,
        ProfileService profiles,
        LifecycleQueryService lifecycle,
        BillingQueryService billing
    ) {
        this.auth = auth;
        this.profiles = profiles;
        this.lifecycle = lifecycle;
        this.billing = billing;
    }

    @GetMapping("/api/v1/me/account-center")
    public Map<String, Object> accountCenter(
        Authentication authentication
    ) {
        var actor = actor(authentication);
        var body = new LinkedHashMap<String, Object>();

        body.put("profile", profile(profiles.get(actor.userId())));

        var lifecycleSnapshot = lifecycle.current(
            actor.userId(),
            Instant.now()
        );
        body.put(
            "lifecycle",
            Map.of(
                "primary",
                lifecycleSnapshot.primary() == null
                    ? Map.of()
                    : lifecycleEntry(lifecycleSnapshot.primary()),
                "contexts",
                lifecycleSnapshot.contexts().stream()
                    .map(this::lifecycleContext)
                    .toList()
            )
        );

        var current = auth.current(actor);
        body.put("currentSession", session(current, true));
        body.put(
            "sessions",
            auth.listSessions(actor).stream()
                .map(value -> session(
                    value,
                    value.getSessionId().equals(actor.sessionId())
                ))
                .toList()
        );
        body.put(
            "loginMethods",
            auth.listIdentities(actor).stream()
                .map(this::identity)
                .toList()
        );

        var subscription = billing.subscription(actor.userId());
        body.put(
            "subscription",
            new LinkedHashMap<String, Object>() {{
                put("planKey", subscription.planKey());
                put("status", subscription.status());
                put("billingPeriodStart", subscription.billingPeriodStart());
                put("billingPeriodEnd", subscription.billingPeriodEnd());
                put("renewsAt", subscription.renewsAt());
                put("cancelAtPeriodEnd", subscription.cancelAtPeriodEnd());
                put("provider", subscription.provider());
                put("actions", subscription.actions());
            }}
        );
        body.put(
            "entitlements",
            billing.entitlements(actor.userId()).stream()
                .map(value -> {
                    var item = new LinkedHashMap<String, Object>();
                    item.put("entitlementCode", value.entitlementCode());
                    item.put("status", value.status());
                    item.put("validFrom", value.validFrom());
                    item.put("validUntil", value.validUntil());
                    item.put("sourceType", value.sourceType());
                    item.put("reasonCode", value.reasonCode());
                    item.put("resolvedAt", value.resolvedAt());
                    return item;
                })
                .toList()
        );

        return body;
    }

    private AuthenticatedActor actor(Authentication authentication) {
        if (
            authentication == null
                || !(authentication.getPrincipal()
                    instanceof AuthenticatedActor actor)
        ) {
            throw new AuthException(
                "AUTH_REQUIRED",
                "Authentication required"
            );
        }
        return actor;
    }

    private Map<String, Object> profile(UserProfileEntity value) {
        if (value == null) return null;

        var preferences = new LinkedHashMap<String, Object>();
        preferences.put(
            "measurementSystem",
            value.getMeasurementSystem()
        );
        preferences.put(
            "unitPreferences",
            value.getUnitPreferences()
        );
        preferences.put(
            "notificationPreferences",
            value.getNotificationPreferences()
        );

        var result = new LinkedHashMap<String, Object>();
        result.put("displayName", value.getDisplayName());
        result.put(
            "birthDate",
            value.getBirthDate() == null
                ? null
                : value.getBirthDate().toString()
        );
        result.put(
            "preferredLanguage",
            value.getPreferredLanguage()
        );
        result.put("locale", value.getLocale());
        result.put("countryRegion", value.getCountryRegion());
        result.put("timezone", value.getTimezone());
        result.put("accountStatus", value.getAccountStatus().name());
        result.put("version", value.getVersion());
        result.put("preferences", preferences);
        return result;
    }

    private Map<String, Object> session(
        AuthSessionEntity value,
        boolean current
    ) {
        var result = new LinkedHashMap<String, Object>();
        result.put("sessionId", value.getSessionId());
        result.put("createdAt", value.getCreatedAt());
        result.put("authenticatedAt", value.getAuthenticatedAt());
        result.put("accessExpiresAt", value.getAccessExpiresAt());
        result.put("refreshExpiresAt", value.getRefreshExpiresAt());
        result.put("status", value.getStatus().name());
        result.put("deviceLabel", value.getDeviceLabel());
        result.put("current", current);
        return result;
    }

    private Map<String, Object> identity(LoginIdentityEntity value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("identityId", value.getIdentityId());
        result.put("provider", value.getProvider().name());
        result.put("verifiedEmail", value.getVerifiedEmail());
        result.put("createdAt", value.getCreatedAt());
        return result;
    }

    private Map<String, Object> lifecycleEntry(
        LifecycleEntry value
    ) {
        var result = new LinkedHashMap<String, Object>();
        result.put("period", value.period().name());
        result.put("substage", value.substage());
        result.put("effectiveFrom", value.effectiveFrom());
        result.put("effectiveTo", value.effectiveTo());
        result.put("confidence", value.confidence());
        result.put("selectedManually", value.selectedManually());
        return result;
    }

    private Map<String, Object> lifecycleContext(
        LifecycleContext value
    ) {
        var result = new LinkedHashMap<String, Object>();
        result.put("contextType", value.contextType());
        result.put("validFrom", value.validFrom());
        result.put("validTo", value.validTo());
        result.put("confidence", value.confidence());
        return result;
    }
}
