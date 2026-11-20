package com.momna.modules.checkin.definition;

import com.momna.platform.safety.SafetyCatalogContribution;
import com.momna.platform.safety.SafetyRoute;
import com.momna.platform.safety.SafetyRule;
import java.util.Locale;
import java.util.Set;

/**
 * Definition-only bridge into the safety platform. Check-in publishes governed content only;
 * the platform owns catalog composition and all evaluation semantics.
 */
public final class CheckinSafetyPlatformContribution {
    public static final String FEATURE = "CHECKIN";

    private CheckinSafetyPlatformContribution() {}

    public static SafetyCatalogContribution contribution(CheckinSafetyRouteCatalog catalog) {
        return new SafetyCatalogContribution(
            FEATURE,
            catalog.metadata().catalogVersion(),
            catalog.routes().stream()
                .map(route -> new SafetyRoute(route.code(), route.disposition(), route.severity(), route.allowedActions()))
                .toList(),
            catalog.bindings().stream()
                .map(binding -> new SafetyRule(
                    "checkin." + binding.itemCode().toLowerCase(Locale.ROOT),
                    catalog.metadata().ruleVersion(),
                    Set.of(binding.itemCode()),
                    binding.routeCode()))
                .toList()
        );
    }
}
