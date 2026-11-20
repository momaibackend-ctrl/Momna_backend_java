package com.momna.platform.safety;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;

/** Composes feature-owned governed content into the shared safety catalog. */
public final class SafetyCatalogComposer {
    private SafetyCatalogComposer() {}

    public static SafetyCatalog compose(Collection<SafetyCatalogContribution> contributions, String activeVersion) {
        return compose(contributions, activeVersion, CanonicalSafetyCatalog.create());
    }

    public static SafetyCatalog compose(
        Collection<SafetyCatalogContribution> contributions,
        String activeVersion,
        SafetyCatalog base
    ) {
        if (contributions.isEmpty()) {
            throw new IllegalArgumentException("At least one safety catalog contribution is required");
        }
        if (contributions.stream().map(SafetyCatalogContribution::catalogVersion).distinct().count()
            != contributions.size()) {
            throw new IllegalArgumentException("Safety catalog contribution versions must be unique");
        }
        var routes = new ArrayList<>(base.routes());
        var rules = new ArrayList<>(base.rules());
        var catalogVersions = new LinkedHashSet<>(base.registeredCatalogVersions());
        var ruleVersions = new LinkedHashSet<>(base.registeredRuleVersions());
        for (var contribution : contributions) {
            routes.addAll(contribution.routes());
            rules.addAll(contribution.rules());
            catalogVersions.add(contribution.catalogVersion());
            contribution.rules().forEach(rule -> ruleVersions.add(rule.version()));
        }
        if (!catalogVersions.contains(activeVersion)) {
            throw new IllegalArgumentException("Active safety catalog version is not registered");
        }
        return new SafetyCatalog(
            activeVersion,
            catalogVersions,
            routes,
            rules,
            ruleVersions,
            base.baselineRuleVersion(),
            base.baselineRouteCode(),
            base.fallbackRouteCode()
        );
    }
}
