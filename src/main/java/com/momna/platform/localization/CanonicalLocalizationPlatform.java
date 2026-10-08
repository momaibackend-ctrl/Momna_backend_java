package com.momna.platform.localization;

import com.momna.platform.observability.StructuredObservabilityService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class CanonicalLocalizationPlatform {
    private final VersionedLocaleResolver resolver;
    private final StaticCountryPolicyRegistry countries;
    private final CanonicalTimeService time;
    private final LocalizationProfileSource profileSource;
    private final StructuredObservabilityService observability;

    public CanonicalLocalizationPlatform(
        VersionedLocaleResolver resolver,
        StaticCountryPolicyRegistry countries,
        CanonicalTimeService time,
        LocalizationProfileSource profileSource,
        StructuredObservabilityService observability
    ) {
        this.resolver = resolver;
        this.countries = countries;
        this.time = time;
        this.profileSource = profileSource;
        this.observability = observability;
    }

    public VersionedLocaleResolver.Policy policy() {
        return resolver.policy();
    }

    public UserLocaleContext localeFor(String userId) {
        if (profileSource == null) {
            throw new LocalizationException(
                "VALIDATION_ERROR",
                "Profile localization source is not configured"
            );
        }
        var context = profileSource.contextFor(userId);
        if (context == null) {
            throw new LocalizationException(
                "VALIDATION_ERROR",
                "Profile localization context not found"
            );
        }

        var resolved = resolve(
            new VersionedLocaleResolver.ResolveRequest(
                List.of(
                    new VersionedLocaleResolver.Candidate(
                        VersionedLocaleResolver.Source.PROFILE,
                        context.language(),
                        context.locale(),
                        context.countryCode(),
                        context.ianaTimezone()
                    )
                ),
                true,
                false,
                true
            )
        );

        return new UserLocaleContext(
            resolved.locale(),
            resolved.countryRegion(),
            resolved.timezone().getId(),
            resolved.language(),
            resolved.policyVersion()
        );
    }

    public VersionedLocaleResolver.ResolvedLocalization resolve(
        VersionedLocaleResolver.ResolveRequest request
    ) {
        return observe(
            "localization.resolve",
            () -> resolver.resolve(request)
        );
    }

    public List<String> fallbackChain(String locale) {
        return observe(
            "localization.fallback",
            () -> resolver.fallbackChain(locale)
        );
    }

    public VersionedLocaleResolver.ContentLocaleResolution resolveContentLocale(
        String contentKey,
        String requestedLocale,
        Set<String> availableLocales
    ) {
        return observe(
            "localization.content.resolve",
            () -> resolver.resolveContentLocale(
                contentKey,
                requestedLocale,
                availableLocales
            )
        );
    }

    public StaticCountryPolicyRegistry.CountryPolicy resolveCountryPolicy(
        String countryRegion,
        String version
    ) {
        return observe(
            "localization.country.resolve",
            () -> countries.resolveCountryPolicy(countryRegion, version)
        );
    }

    public ZonedDateTime getLocalNow(
        ZoneId timezone,
        Clock clock
    ) {
        return observe(
            "localization.time.now",
            () -> time.getLocalNow(timezone, clock)
        );
    }

    public LocalDate getLocalDate(
        ZoneId timezone,
        Instant instant
    ) {
        return observe(
            "localization.time.date",
            () -> time.getLocalDate(timezone, instant)
        );
    }

    public CanonicalTimeService.DayWindow resolveDayWindow(
        LocalDate localDate,
        ZoneId timezone
    ) {
        return observe(
            "localization.time.day_window",
            () -> time.resolveDayWindow(localDate, timezone)
        );
    }

    public CanonicalTimeService.ScheduledLocalTimeResult convertScheduledLocalTime(
        LocalDateTime localDateTime,
        ZoneId timezone,
        AmbiguousLocalTimePolicy ambiguousPolicy,
        NonexistentLocalTimePolicy nonexistentPolicy
    ) {
        return observe(
            "localization.time.schedule",
            () -> time.convertScheduledLocalTime(
                localDateTime,
                timezone,
                ambiguousPolicy,
                nonexistentPolicy
            )
        );
    }

    public CanonicalTimeService.EventLocalTime captureEventLocalTime(
        Instant occurredAt,
        ZoneId timezone
    ) {
        return observe(
            "localization.time.event",
            () -> time.captureEventLocalTime(occurredAt, timezone)
        );
    }

    public CanonicalTimeService.EffectiveLocalDay resolveEffectiveLocalDay(
        ZoneId timezone,
        Instant referenceInstant,
        LocalDate fixedLocalDate
    ) {
        return observe(
            "localization.time.effective_day",
            () -> time.resolveEffectiveLocalDay(
                timezone,
                referenceInstant,
                fixedLocalDate
            )
        );
    }

    private <T> T observe(
        String metric,
        Supplier<T> operation
    ) {
        try {
            var value = operation.get();
            if (observability != null) {
                observability.increment(metric + ".success");
            }
            return value;
        } catch (LocalizationException failure) {
            if (observability != null) {
                observability.increment(metric + ".failure");
            }
            throw failure;
        }
    }

    @FunctionalInterface
    public interface LocalizationProfileSource {
        UserLocaleContext contextFor(String userId);
    }

    public record UserLocaleContext(
        String locale,
        String countryCode,
        String ianaTimezone,
        String language,
        String policyVersion
    ) {}
}
