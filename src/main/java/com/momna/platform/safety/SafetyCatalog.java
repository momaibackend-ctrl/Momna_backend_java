package com.momna.platform.safety;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An immutable, validated set of safety routes and deterministic rules. */
public final class SafetyCatalog {
    private final String version;
    private final Set<String> registeredCatalogVersions;
    private final List<SafetyRoute> routes;
    private final List<SafetyRule> rules;
    private final Set<String> registeredRuleVersions;
    private final String baselineRuleVersion;
    private final String baselineRouteCode;
    private final String fallbackRouteCode;
    private final Map<String, SafetyRoute> routesByCode;

    public SafetyCatalog(
        String version,
        Set<String> registeredCatalogVersions,
        List<SafetyRoute> routes,
        List<SafetyRule> rules,
        Set<String> registeredRuleVersions,
        String baselineRuleVersion,
        String baselineRouteCode,
        String fallbackRouteCode
    ) {
        this.version = version;
        this.registeredCatalogVersions = Collections.unmodifiableSet(new LinkedHashSet<>(registeredCatalogVersions));
        this.routes = List.copyOf(routes);
        this.rules = List.copyOf(rules);
        this.registeredRuleVersions = Collections.unmodifiableSet(new LinkedHashSet<>(registeredRuleVersions));
        this.baselineRuleVersion = baselineRuleVersion;
        this.baselineRouteCode = baselineRouteCode;
        this.fallbackRouteCode = fallbackRouteCode;
        var byCode = new LinkedHashMap<String, SafetyRoute>();
        for (var route : this.routes) byCode.put(route.code(), route);
        this.routesByCode = Collections.unmodifiableMap(byCode);

        require(this.registeredCatalogVersions.contains(version), "Unregistered safety catalog version");
        require(!this.routes.isEmpty(), "Safety catalog requires routes");
        require(routesByCode.size() == this.routes.size(), "Safety route codes must be unique");
        require(this.rules.stream().map(SafetyRule::id).distinct().count() == this.rules.size(),
            "Safety rule ids must be unique");
        require(this.rules.stream().allMatch(r -> this.registeredRuleVersions.contains(r.version())),
            "Unregistered safety rule version");
        require(this.registeredRuleVersions.contains(baselineRuleVersion), "Baseline rule version is not registered");
        require(this.rules.stream().allMatch(r -> routesByCode.containsKey(r.routeCode())),
            "Safety rule references an unregistered route");
        require(routesByCode.containsKey(baselineRouteCode), "Baseline route is not registered");
        require(routesByCode.containsKey(fallbackRouteCode), "Fallback route is not registered");
    }

    public String version() { return version; }
    public Set<String> registeredCatalogVersions() { return registeredCatalogVersions; }
    public List<SafetyRoute> routes() { return routes; }
    public List<SafetyRule> rules() { return rules; }
    public Set<String> registeredRuleVersions() { return registeredRuleVersions; }
    public String baselineRuleVersion() { return baselineRuleVersion; }
    public String baselineRouteCode() { return baselineRouteCode; }
    public String fallbackRouteCode() { return fallbackRouteCode; }

    public SafetyRoute route(String code) {
        var route = routesByCode.get(code);
        if (route == null) throw new IllegalArgumentException("Unregistered safety route");
        return route;
    }

    public SafetyRoute routeOrNull(String code) { return routesByCode.get(code); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
