package com.momna.platform.featureflags;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.platform.cache.CacheKey;
import com.momna.platform.cache.ExpiringCache;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigFeatureFlagService {
    private static final Set<String> PROTECTED_MODULES = Set.of(
        "platform.privacy", "platform.safety", "platform.audit", "platform.observability"
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ObjectProvider<ExpiringCache> cache;

    public ConfigFeatureFlagService(
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        ObjectProvider<ExpiringCache> cache
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public ResolvedConfig resolveConfig(
        String key,
        ConfigEnvironment environment,
        Instant effectiveAt
    ) {
        requireIdentifier(key, "config key");

        var rows = jdbc.query(
            """
            select d.config_key,d.value_type,d.owner,d.required,d.bootstrap_allowed,
                   v.version,v.schema_version,v.value_type as version_value_type,
                   v.value_json,v.status,v.valid_from,v.valid_until,v.row_version
            from momna.config_definitions d
            join momna.config_versions v on v.config_key=d.config_key
            where d.config_key=? and v.environment=? and v.status='PUBLISHED'
              and v.valid_from<=? and (v.valid_until is null or ?>v.valid_from and ?<v.valid_until)
            order by v.version desc
            limit 1
            """,
            (rs, rowNum) -> config(rs),
            key,
            environment.name(),
            java.sql.Timestamp.from(effectiveAt),
            java.sql.Timestamp.from(effectiveAt),
            java.sql.Timestamp.from(effectiveAt)
        );
        if (rows.isEmpty()) {
            throw new ConfigFeatureFlagException("CONFIG_NOT_FOUND", "No effective config version");
        }
        var value = rows.getFirst();
        if (value.valueType() != value.versionValueType()) {
            throw new ConfigFeatureFlagException("INVALID_VALUE", "Config value type mismatch");
        }
        return value;
    }

    @Transactional(readOnly = true)
    public FlagEvaluation evaluateFlag(
        String flagKey,
        ConfigEnvironment environment,
        EvaluationContext context,
        Integer pinnedVersion
    ) {
        requireIdentifier(flagKey, "flag key");
        requireIdentifier(context.subjectRef(), "subjectRef");
        if (!context.privacyAllowed() || !context.safetyAllowed()) {
            throw new ConfigFeatureFlagException(
                "FORBIDDEN",
                "Feature flags cannot bypass Privacy/Consent or Safety decisions"
            );
        }

        var definitionExists = Boolean.TRUE.equals(jdbc.queryForObject(
            "select exists(select 1 from momna.feature_flag_definitions where flag_key=?)",
            Boolean.class,
            flagKey
        ));
        if (!definitionExists) {
            throw new ConfigFeatureFlagException("FLAG_NOT_FOUND", "Feature flag definition is not registered");
        }

        var version = pinnedVersion == null
            ? resolveFlagVersion(flagKey, environment, context.effectiveAt())
            : exactFlagVersion(flagKey, environment, pinnedVersion);

        if (version == null) {
            throw new ConfigFeatureFlagException(
                pinnedVersion == null ? "FLAG_NOT_FOUND" : "VERSION_NOT_FOUND",
                "Feature flag version is not found"
            );
        }
        if (!version.effectiveAt(context.effectiveAt())) {
            throw new ConfigFeatureFlagException("VERSION_NOT_FOUND", "Feature flag version is not effective");
        }

        var cacheKey = evaluationCacheKey(flagKey, environment, version, context);
        var cacheProvider = cache.getIfAvailable();
        if (cacheProvider != null) {
            var encoded = cacheProvider.get(cacheKey);
            var cached = decode(version, encoded);
            if (cached != null) return cached;
        }

        var matches = new ArrayList<MatchedRule>();
        for (var rule : version.rules()) {
            Segment segment = null;
            if (rule.segmentId() != null) {
                segment = findSegment(rule.segmentId(), environment, context.effectiveAt());
                if (segment == null) {
                    throw new ConfigFeatureFlagException(
                        "UNKNOWN_SEGMENT",
                        "Referenced feature-flag segment is not registered/effective"
                    );
                }
            }

            var segmentMatches = segment == null || matchesSegment(segment, context);
            var rolloutMatches = rule.rolloutBasisPoints() == null
                || deterministicBucket(version, context.subjectRef()) < rule.rolloutBasisPoints();
            if (segmentMatches && rolloutMatches) {
                matches.add(new MatchedRule(rule, segment));
            }
        }

        var highest = matches.stream()
            .mapToInt(x -> x.rule().priority())
            .max()
            .orElse(Integer.MIN_VALUE);
        var top = highest == Integer.MIN_VALUE
            ? List.<MatchedRule>of()
            : matches.stream().filter(x -> x.rule().priority() == highest).toList();

        if (top.size() > 1) {
            throw new ConfigFeatureFlagException(
                "AMBIGUOUS_RULE",
                "Multiple rules matched at the same precedence"
            );
        }

        FlagEvaluation result;
        if (top.isEmpty()) {
            result = new FlagEvaluation(
                flagKey,
                version.version(),
                version.defaultEnabled(),
                "DEFAULT",
                version.rolloutAlgorithmVersion(),
                null,
                null
            );
        } else {
            var selected = top.getFirst();
            result = new FlagEvaluation(
                flagKey,
                version.version(),
                selected.rule().enabled(),
                "RULE_MATCH",
                version.rolloutAlgorithmVersion(),
                selected.rule().id(),
                selected.segment() == null ? null : selected.segment().id()
            );
        }

        if (cacheProvider != null) {
            cacheProvider.put(cacheKey, encode(result), Duration.ofMinutes(5));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ModuleEvaluation evaluateModule(
        String moduleId,
        ConfigEnvironment environment,
        EvaluationContext context
    ) {
        requireIdentifier(moduleId, "moduleId");
        if (!context.privacyAllowed() || !context.safetyAllowed()) {
            throw new ConfigFeatureFlagException("FORBIDDEN", "Module evaluation denied by policy");
        }
        if (PROTECTED_MODULES.contains(moduleId)) {
            return new ModuleEvaluation(moduleId, true, "PROTECTED_PLATFORM_MODULE", null);
        }

        var keys = jdbc.query(
            """
            select d.flag_key
            from momna.feature_flag_definitions d
            join momna.feature_flag_versions v on v.flag_key=d.flag_key
            where d.module_id=? and v.environment=? and v.status='PUBLISHED'
              and v.valid_from<=? and (v.valid_until is null or ?<v.valid_until)
            order by v.version desc
            limit 1
            """,
            (rs, rowNum) -> rs.getString(1),
            moduleId,
            environment.name(),
            java.sql.Timestamp.from(context.effectiveAt()),
            java.sql.Timestamp.from(context.effectiveAt())
        );
        if (keys.isEmpty()) {
            return new ModuleEvaluation(moduleId, true, "NO_KILL_SWITCH", null);
        }

        var flag = evaluateFlag(keys.getFirst(), environment, context, null);
        return new ModuleEvaluation(
            moduleId,
            flag.enabled(),
            flag.enabled() ? "MODULE_ENABLED" : "MODULE_DISABLED",
            flag
        );
    }

    private FlagVersion resolveFlagVersion(
        String key,
        ConfigEnvironment environment,
        Instant effectiveAt
    ) {
        var rows = jdbc.query(
            """
            select * from momna.feature_flag_versions
            where flag_key=? and environment=? and status='PUBLISHED'
              and valid_from<=? and (valid_until is null or ?<valid_until)
            order by version desc
            limit 1
            """,
            (rs, rowNum) -> flagVersion(rs),
            key,
            environment.name(),
            java.sql.Timestamp.from(effectiveAt),
            java.sql.Timestamp.from(effectiveAt)
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private FlagVersion exactFlagVersion(String key, ConfigEnvironment environment, int version) {
        var rows = jdbc.query(
            """
            select * from momna.feature_flag_versions
            where flag_key=? and environment=? and version=?
            """,
            (rs, rowNum) -> flagVersion(rs),
            key,
            environment.name(),
            version
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Segment findSegment(String id, ConfigEnvironment environment, Instant at) {
        var rows = jdbc.query(
            """
            select * from momna.feature_flag_segments
            where segment_id=? and environment=? and status='PUBLISHED'
              and valid_from<=? and (valid_until is null or ?<valid_until)
            order by version desc
            limit 1
            """,
            (rs, rowNum) -> segment(rs),
            id,
            environment.name(),
            java.sql.Timestamp.from(at),
            java.sql.Timestamp.from(at)
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private boolean matchesSegment(Segment segment, EvaluationContext context) {
        if (!segment.countries().isEmpty() && !segment.countries().contains(context.countryRegion())) return false;
        if (!segment.locales().isEmpty() && !segment.locales().contains(context.locale())) return false;
        return true;
    }

    private int deterministicBucket(FlagVersion version, String subjectRef) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(
                (version.rolloutAlgorithmVersion() + "|" + version.key() + "|" + version.version() + "|" + subjectRef)
                    .getBytes(StandardCharsets.UTF_8)
            );
            var value = ByteBuffer.wrap(Arrays.copyOfRange(bytes, 0, 8)).getLong();
            return (int) Long.remainderUnsigned(value, 10_000L);
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private CacheKey evaluationCacheKey(
        String flagKey,
        ConfigEnvironment environment,
        FlagVersion version,
        EvaluationContext context
    ) {
        try {
            var raw = String.join("|",
                environment.name(),
                flagKey,
                Integer.toString(version.version()),
                context.subjectRef(),
                context.countryRegion() == null ? "-" : context.countryRegion(),
                context.locale() == null ? "-" : context.locale(),
                Boolean.toString(context.privacyAllowed()),
                Boolean.toString(context.safetyAllowed())
            );
            var bytes = MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8));
            var out = new StringBuilder(64);
            for (byte b : bytes) out.append("%02x".formatted(b & 0xff));
            return new CacheKey("config-featureflags", "flag-" + version.version(), out.toString());
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private FlagVersion flagVersion(ResultSet rs) throws SQLException {
        var rules = readRules(rs.getString("rules"));
        var validUntil = rs.getTimestamp("valid_until");
        return new FlagVersion(
            rs.getString("flag_key"),
            rs.getInt("version"),
            rs.getInt("schema_version"),
            ConfigEnvironment.valueOf(rs.getString("environment")),
            ConfigVersionStatus.valueOf(rs.getString("status")),
            rs.getBoolean("default_enabled"),
            rules,
            rs.getString("rollout_algorithm_version"),
            rs.getTimestamp("valid_from").toInstant(),
            validUntil == null ? null : validUntil.toInstant()
        );
    }

    private Segment segment(ResultSet rs) throws SQLException {
        var validUntil = rs.getTimestamp("valid_until");
        return new Segment(
            rs.getString("segment_id"),
            rs.getInt("version"),
            ConfigEnvironment.valueOf(rs.getString("environment")),
            readStringSet(rs.getString("countries")),
            readStringSet(rs.getString("locales")),
            rs.getTimestamp("valid_from").toInstant(),
            validUntil == null ? null : validUntil.toInstant()
        );
    }

    private ResolvedConfig config(ResultSet rs) throws SQLException {
        var validUntil = rs.getTimestamp("valid_until");
        return new ResolvedConfig(
            rs.getString("config_key"),
            ConfigValueType.valueOf(rs.getString("value_type")),
            ConfigValueType.valueOf(rs.getString("version_value_type")),
            rs.getString("owner"),
            rs.getBoolean("required"),
            rs.getBoolean("bootstrap_allowed"),
            rs.getInt("version"),
            rs.getInt("schema_version"),
            readJson(rs.getString("value_json")),
            ConfigVersionStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("valid_from").toInstant(),
            validUntil == null ? null : validUntil.toInstant(),
            rs.getLong("row_version")
        );
    }

    private List<Rule> readRules(String json) {
        try {
            var raw = mapper.readValue(json, new TypeReference<List<Map<String,Object>>>() {});
            var out = new ArrayList<Rule>();
            for (var item : raw) {
                var priority = Integer.parseInt(String.valueOf(item.get("priority")));
                var basis = item.get("rolloutBasisPoints");
                var rule = new Rule(
                    String.valueOf(item.get("id")),
                    priority,
                    Boolean.parseBoolean(String.valueOf(item.get("enabled"))),
                    item.get("segmentId") == null ? null : String.valueOf(item.get("segmentId")),
                    basis == null ? null : Integer.parseInt(String.valueOf(basis))
                );
                if (priority < 0 || priority > 1_000_000
                    || (rule.rolloutBasisPoints() != null
                        && (rule.rolloutBasisPoints() < 0 || rule.rolloutBasisPoints() > 10_000))
                    || (rule.segmentId() == null && rule.rolloutBasisPoints() == null)) {
                    throw new ConfigFeatureFlagException("INVALID_RULE", "Invalid feature flag rule");
                }
                out.add(rule);
            }
            return List.copyOf(out);
        } catch (ConfigFeatureFlagException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new ConfigFeatureFlagException("INVALID_RULE", "Feature flag rules cannot be decoded");
        }
    }

    private Set<String> readStringSet(String json) {
        try {
            return Set.copyOf(mapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (Exception failure) {
            throw new ConfigFeatureFlagException("VALIDATION_ERROR", "Feature flag segment cannot be decoded");
        }
    }

    private Object readJson(String json) {
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception failure) {
            throw new ConfigFeatureFlagException("INVALID_VALUE", "Config value cannot be decoded");
        }
    }

    private String encode(FlagEvaluation value) {
        return String.join("|",
            Boolean.toString(value.enabled()),
            value.decisionCode(),
            value.matchedRuleId() == null ? "-" : value.matchedRuleId(),
            value.matchedSegmentId() == null ? "-" : value.matchedSegmentId()
        );
    }

    private FlagEvaluation decode(FlagVersion version, String encoded) {
        if (encoded == null) return null;
        var parts = encoded.split("\\|", -1);
        if (parts.length != 4 || (!"true".equals(parts[0]) && !"false".equals(parts[0]))) return null;
        return new FlagEvaluation(
            version.key(),
            version.version(),
            Boolean.parseBoolean(parts[0]),
            parts[1],
            version.rolloutAlgorithmVersion(),
            "-".equals(parts[2]) ? null : parts[2],
            "-".equals(parts[3]) ? null : parts[3]
        );
    }

    private void requireIdentifier(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9_.:/-]{1,160}")) {
            throw new IllegalArgumentException(name + " must be a bounded technical identifier");
        }
    }

    public record EvaluationContext(
        String subjectRef,
        String countryRegion,
        String locale,
        Instant effectiveAt,
        boolean privacyAllowed,
        boolean safetyAllowed
    ) {}

    public record Rule(
        String id,
        int priority,
        boolean enabled,
        String segmentId,
        Integer rolloutBasisPoints
    ) {}

    public record FlagVersion(
        String key,
        int version,
        int schemaVersion,
        ConfigEnvironment environment,
        ConfigVersionStatus status,
        boolean defaultEnabled,
        List<Rule> rules,
        String rolloutAlgorithmVersion,
        Instant validFrom,
        Instant validUntil
    ) {
        boolean effectiveAt(Instant at) {
            return status == ConfigVersionStatus.PUBLISHED
                && !at.isBefore(validFrom)
                && (validUntil == null || at.isBefore(validUntil));
        }
    }

    public record Segment(
        String id,
        int version,
        ConfigEnvironment environment,
        Set<String> countries,
        Set<String> locales,
        Instant validFrom,
        Instant validUntil
    ) {}

    private record MatchedRule(Rule rule, Segment segment) {}

    public record FlagEvaluation(
        String key,
        int version,
        boolean enabled,
        String decisionCode,
        String rolloutAlgorithmVersion,
        String matchedRuleId,
        String matchedSegmentId
    ) {}

    public record ModuleEvaluation(
        String moduleId,
        boolean enabled,
        String decisionCode,
        FlagEvaluation flag
    ) {}

    public record ResolvedConfig(
        String key,
        ConfigValueType valueType,
        ConfigValueType versionValueType,
        String owner,
        boolean required,
        boolean bootstrapAllowed,
        int version,
        int schemaVersion,
        Object value,
        ConfigVersionStatus status,
        Instant validFrom,
        Instant validUntil,
        long rowVersion
    ) {}
}
