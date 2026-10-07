package com.momna.modules.content;

import com.momna.core.privacy.*;
import com.momna.modules.content.infrastructure.*;
import com.momna.platform.cache.*;
import com.momna.platform.safety.CanonicalSafetyService;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContentResolutionService {
    private final ContentObjectRepository objects;
    private final ContentVersionRepository versions;
    private final PrivacyPolicyService privacy;
    private final CanonicalSafetyService safety;
    private final ObjectProvider<ExpiringCache> cache;

    public ContentResolutionService(
        ContentObjectRepository objects,
        ContentVersionRepository versions,
        PrivacyPolicyService privacy,
        CanonicalSafetyService safety,
        ObjectProvider<ExpiringCache> cache
    ) {
        this.objects = objects;
        this.versions = versions;
        this.privacy = privacy;
        this.safety = safety;
        this.cache = cache;
    }

    public ResolvedContent resolve(
        String contentKey,
        String requestedLocale,
        String countryRegion,
        Instant effectiveAt,
        String audienceKey,
        String purpose,
        String traceId,
        String subjectUserId,
        Integer pinnedVersion
    ) {
        var object = objects.findByContentKey(contentKey)
            .orElseThrow(() -> new ContentException("CONTENT_NOT_FOUND", "Content object not found"));

        ContentVersionEntity version;
        if (pinnedVersion != null) {
            version = versions.findById(new ContentVersionId(object.getContentId(), pinnedVersion))
                .orElseThrow(() -> new ContentException("VERSION_NOT_FOUND", "Content version not found"));
            if (version.getStatus() != ContentStatus.PUBLISHED
                || version.getPublishedAt() == null
                || version.getPublishedAt().isAfter(effectiveAt)) {
                throw new ContentException("CONTENT_UNAVAILABLE", "Pinned content version is not effective");
            }
        } else {
            version = versions.findFirstByContentIdAndStatusAndPublishedAtLessThanEqualOrderByVersionDesc(
                object.getContentId(), ContentStatus.PUBLISHED, effectiveAt
            ).orElseThrow(() -> new ContentException("CONTENT_UNAVAILABLE", "No published content version is effective"));
        }

        if (version.getContentType() != object.getContentType()) {
            throw new ContentException("INVALID_CONTENT", "Content object/version type mismatch");
        }

        var eligible = version.getVariants().stream()
            .filter(v -> audienceKey.equals(String.valueOf(v.getOrDefault("audienceKey", "general"))))
            .filter(v -> active(v, effectiveAt))
            .toList();
        if (eligible.isEmpty()) throw new ContentException("CONTENT_UNAVAILABLE", "No content variant is effective");

        var localeChain = fallbackChain(requestedLocale);
        Map<String,Object> chosen = null;
        String resolvedLocale = null;
        for (var locale : localeChain) {
            var candidates = eligible.stream()
                .filter(v -> locale.equals(normalizeLocale(String.valueOf(v.get("locale")))))
                .filter(v -> countryAllowed(v, countryRegion))
                .toList();
            if (candidates.isEmpty()) continue;

            var specific = candidates.stream()
                .filter(v -> countryRegion != null && countries(v).contains(countryRegion))
                .toList();
            var finalists = specific.isEmpty() ? candidates.stream()
                .filter(v -> countries(v).isEmpty()).toList() : specific;
            if (finalists.size() != 1) {
                throw new ContentException("AMBIGUOUS_VARIANT", "Multiple equally specific content variants");
            }
            chosen = finalists.getFirst();
            resolvedLocale = locale;
            break;
        }
        if (chosen == null) throw new ContentException("VARIANT_NOT_FOUND", "No locale/country variant");

        var scopeName = String.valueOf(chosen.getOrDefault("privacyScope", "PUBLIC_CONTENT"));
        var scope = PrivacyScope.valueOf(scopeName);
        if (scope != PrivacyScope.PUBLIC_CONTENT) {
            if (subjectUserId == null || subjectUserId.isBlank()) {
                throw new ContentException("FORBIDDEN", "Protected content requires authenticated subject");
            }
            var decision = privacy.decide(new PrivacyPolicyService.Request(
                new PrivacyPolicyService.Subject(subjectUserId, subjectUserId, true, "USER"),
                new PrivacyPolicyService.Resource(
                    object.getContentId() + ":" + String.valueOf(chosen.get("id")),
                    subjectUserId,
                    "content",
                    scope,
                    null,
                    false
                ),
                PolicyAction.READ,
                new PrivacyPolicyService.Context(
                    purpose, traceId, CanonicalPrivacyPolicyService.CURRENT_POLICY_VERSION,
                    null, null, scope, null, false
                )
            ));
            if (!decision.allowed()) throw new ContentException("FORBIDDEN", "Content privacy policy denied resolution");
        }

        var signals = strings(chosen.get("safetySignalKeys"));
        if (!signals.isEmpty() && safety.evaluate(signals).blocked()) {
            throw new ContentException("FORBIDDEN", "Safety policy blocked content resolution");
        }

        var cacheStatus = "BYPASS";
        var cacheProvider = cache.getIfAvailable();
        if (cacheProvider != null) {
            var cacheKey = new CacheKey(
                "content-resolution", "v1",
                String.join("|",
                    object.getContentId(),
                    Integer.toString(version.getVersion()),
                    resolvedLocale,
                    countryRegion == null ? "none" : countryRegion,
                    audienceKey,
                    subjectUserId == null ? "public" : subjectUserId
                )
            );
            var expected = version.getVersion() + ":" + String.valueOf(chosen.get("id"));
            var cached = cacheProvider.get(cacheKey);
            if (expected.equals(cached)) {
                cacheStatus = "HIT";
            } else {
                cacheProvider.put(cacheKey, expected, java.time.Duration.ofMinutes(10));
                cacheStatus = "MISS";
            }
        }

        return new ResolvedContent(
            object.getContentId(), contentKey, object.getContentType().name(),
            version.getVersion(), version.getSchemaVersion(), resolvedLocale,
            chosen, cacheStatus, version.getSourceType().name()
        );
    }

    private boolean active(Map<String,Object> variant, Instant at) {
        var from = parseInstant(variant.get("validFrom"));
        var until = parseInstant(variant.get("validUntil"));
        return (from == null || !at.isBefore(from)) && (until == null || at.isBefore(until));
    }

    private boolean countryAllowed(Map<String,Object> variant, String country) {
        var values = countries(variant);
        return country == null || values.isEmpty() || values.contains(country);
    }

    private Set<String> countries(Map<String,Object> variant) {
        return strings(variant.get("countryRegions"));
    }

    private Set<String> strings(Object raw) {
        if (!(raw instanceof Collection<?> c)) return Set.of();
        return c.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private List<String> fallbackChain(String locale) {
        var normalized = normalizeLocale(locale);
        var out = new ArrayList<String>();
        out.add(normalized);
        var language = normalized.contains("-") ? normalized.substring(0, normalized.indexOf('-')) : normalized;
        if (!out.contains(language)) out.add(language);
        if (!out.contains("en-US")) out.add("en-US");
        return List.copyOf(out);
    }

    private String normalizeLocale(String value) {
        if (value == null || value.isBlank()) throw new ContentException("VALIDATION_ERROR", "Locale is required");
        var parts = value.trim().replace('_','-').split("-");
        var language = parts[0].toLowerCase(Locale.ROOT);
        if (parts.length == 1) return language;
        return language + "-" + parts[1].toUpperCase(Locale.ROOT);
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        return Instant.parse(String.valueOf(value));
    }

    public record ResolvedContent(
        String contentId,
        String contentKey,
        String contentType,
        int version,
        int schemaVersion,
        String resolvedLocale,
        Map<String,Object> variant,
        String cacheStatus,
        String sourceType
    ) {}

    public static class ContentException extends RuntimeException {
        private final String code;
        public ContentException(String code, String message) {
            super(message);
            this.code = code;
        }
        public String code() { return code; }
    }
}
