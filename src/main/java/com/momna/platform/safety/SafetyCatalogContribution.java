package com.momna.platform.safety;

import java.util.List;

/** Feature-owned governed content composed into the shared safety catalog. */
public record SafetyCatalogContribution(
    String feature,
    String catalogVersion,
    List<SafetyRoute> routes,
    List<SafetyRule> rules
) {
    public SafetyCatalogContribution {
        routes = List.copyOf(routes);
        rules = List.copyOf(rules);
    }
}
