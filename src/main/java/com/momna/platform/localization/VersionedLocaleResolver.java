package com.momna.platform.localization;

import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

public final class VersionedLocaleResolver {
    private final Policy policy;

    public VersionedLocaleResolver(Policy policy) {
        this.policy = Objects.requireNonNull(policy);
        if (policy.version() == null || policy.version().isBlank()) {
            throw new IllegalArgumentException("Localization policy version is required");
        }
        if (policy.supportedLocales() == null || policy.supportedLocales().isEmpty()) {
            throw new IllegalArgumentException("Supported locales are required");
        }
        if (!normalizedSupported().contains(normalizeLocale(policy.fallbackLocale()))) {
            throw new IllegalArgumentException("Fallback locale must be supported");
        }
    }

    public Policy policy() {
        return policy;
    }

    public ResolvedLocalization resolve(ResolveRequest request) {
        var ordered = new ArrayList<Candidate>();
        for (var source : policy.sourcePrecedence()) {
            request.candidates().stream()
                .filter(c -> c.source() == source)
                .findFirst()
                .ifPresent(ordered::add);
        }

        var languagePair = first(ordered, Candidate::language);
        var localePair = first(ordered, Candidate::locale);
        var countryPair = first(ordered, Candidate::countryRegion);
        var timezonePair = first(ordered, Candidate::timezone);

        String language = languagePair == null ? null : normalizeLanguage(languagePair.value());
        String locale = localePair == null ? null : normalizeLocale(localePair.value());
        Source languageSource = languagePair == null ? null : languagePair.source();
        Source localeSource = localePair == null ? null : localePair.source();

        if (locale == null && language != null && policy.deriveLocaleFromLanguage()) {
            final String languageValue = language;
            locale = normalizedSupported().stream()
                .filter(v -> v.equals(languageValue) || v.startsWith(languageValue + "-"))
                .findFirst()
                .orElse(null);
            if (locale != null) localeSource = Source.DEFAULT_POLICY;
        }
        if (language == null && locale != null && policy.deriveLanguageFromLocale()) {
            language = normalizeLanguage(locale.substring(0, locale.indexOf('-') > 0 ? locale.indexOf('-') : locale.length()));
            languageSource = Source.DEFAULT_POLICY;
        }

        var chain = locale == null ? List.<String>of() : fallbackChain(locale);
        if (locale != null && !normalizedSupported().contains(locale)) {
            if (!policy.allowUnsupportedLocaleFallback()) {
                throw new LocalizationException("UNSUPPORTED_LOCALE", "Unsupported locale");
            }
            locale = chain.stream().filter(normalizedSupported()::contains).findFirst()
                .orElseThrow(() -> new LocalizationException("UNSUPPORTED_LOCALE", "No supported locale fallback"));
            localeSource = Source.DEFAULT_POLICY;
            chain = fallbackChain(locale);
        }

        if (request.requireLocale() && locale == null) {
            throw new LocalizationException("LOCALE_REQUIRED", "Locale is required");
        }
        var country = countryPair == null ? null : normalizeCountry(countryPair.value());
        if (request.requireCountry() && country == null) {
            throw new LocalizationException("COUNTRY_REQUIRED", "Country is required");
        }
        var timezone = timezonePair == null ? null : parseTimezone(timezonePair.value());
        if (request.requireTimezone() && timezone == null) {
            throw new LocalizationException("TIMEZONE_REQUIRED", "Timezone is required");
        }

        return new ResolvedLocalization(
            language, locale, country, timezone,
            languageSource, localeSource,
            countryPair == null ? null : countryPair.source(),
            timezonePair == null ? null : timezonePair.source(),
            chain, policy.version()
        );
    }

    public List<String> fallbackChain(String locale) {
        var normalized = normalizeLocale(locale);
        var values = new ArrayList<String>();
        values.add(normalized);
        for (var value : policy.explicitFallbacks().getOrDefault(normalized, List.of())) {
            var normalizedValue = normalizeLocale(value);
            if (!values.contains(normalizedValue)) values.add(normalizedValue);
        }
        var dash = normalized.indexOf('-');
        if (dash > 0) {
            var language = normalized.substring(0, dash);
            if (!values.contains(language)) values.add(language);
        }
        var fallback = normalizeLocale(policy.fallbackLocale());
        if (!values.contains(fallback)) values.add(fallback);
        return List.copyOf(values);
    }

    public ContentLocaleResolution resolveContentLocale(
        String contentKey,
        String requestedLocale,
        Set<String> availableLocales
    ) {
        if (contentKey == null || contentKey.isBlank()) {
            throw new LocalizationException("VALIDATION_ERROR", "Content key is required");
        }
        var available = availableLocales.stream().map(this::normalizeLocale).collect(java.util.stream.Collectors.toSet());
        var chain = fallbackChain(requestedLocale);
        var selected = chain.stream().filter(available::contains).findFirst()
            .orElseThrow(() -> new LocalizationException("CONTENT_VARIANT_NOT_FOUND", "No content locale variant"));
        return new ContentLocaleResolution(
            contentKey,
            normalizeLocale(requestedLocale),
            selected,
            chain,
            policy.version()
        );
    }

    private Set<String> normalizedSupported() {
        return policy.supportedLocales().stream().map(this::normalizeLocale)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String normalizeLanguage(String value) {
        var normalized = value.trim().replace('_', '-');
        if (!normalized.matches("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$")) {
            throw new LocalizationException("INVALID_LANGUAGE", "Invalid language tag");
        }
        var dash = normalized.indexOf('-');
        return (dash < 0 ? normalized : normalized.substring(0, dash)).toLowerCase(Locale.ROOT);
    }

    private String normalizeLocale(String value) {
        if (value == null) throw new LocalizationException("INVALID_LOCALE", "Invalid locale tag");
        var raw = value.trim().replace('_', '-');
        if (!raw.matches("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$")) {
            throw new LocalizationException("INVALID_LOCALE", "Invalid locale tag");
        }
        var parts = raw.split("-");
        var normalized = new ArrayList<String>();
        normalized.add(parts[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            var part = parts[i];
            if (part.length() == 2 && part.chars().allMatch(Character::isLetter)) {
                normalized.add(part.toUpperCase(Locale.ROOT));
            } else if (part.length() == 3 && part.chars().allMatch(Character::isDigit)) {
                normalized.add(part.toUpperCase(Locale.ROOT));
            } else if (part.length() == 4 && part.chars().allMatch(Character::isLetter)) {
                normalized.add(part.substring(0,1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT));
            } else {
                normalized.add(part);
            }
        }
        return String.join("-", normalized);
    }

    private String normalizeCountry(String value) {
        var normalized = value.trim().replace('_', '-').toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z]{2}(?:-[A-Z0-9]{1,3})*$")) {
            throw new LocalizationException("INVALID_COUNTRY", "Invalid country/region");
        }
        return normalized;
    }

    private ZoneId parseTimezone(String value) {
        try {
            var zone = ZoneId.of(value.trim());
            if (!ZoneId.getAvailableZoneIds().contains(zone.getId())) {
                throw new LocalizationException("INVALID_TIMEZONE", "Timezone must be an IANA zone ID");
            }
            return zone;
        } catch (LocalizationException error) {
            throw error;
        } catch (Exception error) {
            throw new LocalizationException("INVALID_TIMEZONE", "Invalid timezone");
        }
    }

    private Pair first(List<Candidate> candidates, Function<Candidate,String> selector) {
        for (var candidate : candidates) {
            var value = selector.apply(candidate);
            if (value != null && !value.isBlank()) return new Pair(candidate.source(), value);
        }
        return null;
    }

    private record Pair(Source source, String value) {}

    public enum Source { EXPLICIT_USER, PROFILE, REQUEST, DEVICE, DEFAULT_POLICY }

    public record Candidate(
        Source source,
        String language,
        String locale,
        String countryRegion,
        String timezone
    ) {}

    public record ResolveRequest(
        List<Candidate> candidates,
        boolean requireLocale,
        boolean requireCountry,
        boolean requireTimezone
    ) {
        public ResolveRequest {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record Policy(
        String version,
        Set<String> supportedLocales,
        String fallbackLocale,
        List<Source> sourcePrecedence,
        Map<String,List<String>> explicitFallbacks,
        boolean allowUnsupportedLocaleFallback,
        boolean deriveLanguageFromLocale,
        boolean deriveLocaleFromLanguage
    ) {
        public Policy {
            supportedLocales = Set.copyOf(supportedLocales);
            sourcePrecedence = sourcePrecedence == null || sourcePrecedence.isEmpty()
                ? List.of(Source.EXPLICIT_USER, Source.PROFILE, Source.REQUEST, Source.DEVICE, Source.DEFAULT_POLICY)
                : List.copyOf(sourcePrecedence);
            explicitFallbacks = explicitFallbacks == null ? Map.of() : Map.copyOf(explicitFallbacks);
        }
    }

    public record ResolvedLocalization(
        String language,
        String locale,
        String countryRegion,
        ZoneId timezone,
        Source languageSource,
        Source localeSource,
        Source countrySource,
        Source timezoneSource,
        List<String> localeFallbackChain,
        String policyVersion
    ) {}

    public record ContentLocaleResolution(
        String contentKey,
        String requestedLocale,
        String resolvedLocale,
        List<String> fallbackChain,
        String policyVersion
    ) {}
}
