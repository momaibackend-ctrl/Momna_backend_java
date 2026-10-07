package com.momna.platform.localization;

import java.util.*;
import java.util.stream.Collectors;

public final class StaticCountryPolicyRegistry {
    private final Map<String, Map<String, CountryPolicy>> byCountry;

    public StaticCountryPolicyRegistry(Collection<CountryPolicy> policies) {
        this.byCountry = policies.stream().collect(
            Collectors.groupingBy(
                policy -> normalize(policy.countryRegion()),
                Collectors.toUnmodifiableMap(
                    CountryPolicy::version,
                    policy -> policy
                )
            )
        );
    }

    public CountryPolicy resolveCountryPolicy(
        String countryRegion,
        String version
    ) {
        var versions = byCountry.get(normalize(countryRegion));
        if (versions == null || versions.isEmpty()) {
            throw new LocalizationException(
                "COUNTRY_POLICY_NOT_FOUND",
                "Country policy not registered"
            );
        }

        if (version == null) {
            return versions.values().stream()
                .max(Comparator.comparing(CountryPolicy::version))
                .orElseThrow();
        }

        var policy = versions.get(version);
        if (policy == null) {
            throw new LocalizationException(
                "COUNTRY_POLICY_NOT_FOUND",
                "Country policy version not registered"
            );
        }
        return policy;
    }

    private String normalize(String value) {
        if (value == null) {
            throw new LocalizationException(
                "INVALID_COUNTRY",
                "Invalid country/region"
            );
        }
        var normalized = value.trim()
            .replace('_', '-')
            .toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z]{2}(?:-[A-Z0-9]{1,3})*$")) {
            throw new LocalizationException(
                "INVALID_COUNTRY",
                "Invalid country/region"
            );
        }
        return normalized;
    }

    public enum MeasurementSystem {
        METRIC,
        US_CUSTOMARY
    }

    public record CountryPolicy(
        String countryRegion,
        String version,
        String legalCopyPolicyKey,
        String safetyCopyPolicyKey,
        Map<String, Boolean> featureAvailability,
        MeasurementSystem measurementSystem,
        Integer minimumAge,
        boolean marketAllowed,
        String notificationVariantKey,
        String legalVariantKey
    ) {
        public CountryPolicy {
            featureAvailability = featureAvailability == null
                ? Map.of()
                : Map.copyOf(featureAvailability);
            measurementSystem = measurementSystem == null
                ? MeasurementSystem.METRIC
                : measurementSystem;
        }
    }
}
