package com.momna.core.context.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.core.context.infrastructure.*;
import com.momna.core.fields.application.CanonicalFieldRegistryService;
import com.momna.core.fields.infrastructure.CanonicalFieldValueEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContextSnapshotService {
    private final ContextPurposePolicyRepository policies;
    private final ContextSnapshotRepository snapshots;
    private final CanonicalFieldRegistryService fields;
    private final ObjectProvider<ContextAccessGuard> accessGuard;
    private final ObjectMapper mapper;

    public ContextSnapshotService(
        ContextPurposePolicyRepository policies,
        ContextSnapshotRepository snapshots,
        CanonicalFieldRegistryService fields,
        ObjectProvider<ContextAccessGuard> accessGuard,
        ObjectMapper mapper
    ) {
        this.policies = policies;
        this.snapshots = snapshots;
        this.fields = fields;
        this.accessGuard = accessGuard;
        this.mapper = mapper.copy().configure(
            com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true
        );
    }

    @Transactional
    public ContextSnapshotEntity createSnapshot(
        String userId,
        String purpose,
        String consumer,
        String operationId,
        Instant referenceAt
    ) {
        requireText(userId, "userId");
        requireText(purpose, "purpose");
        requireText(consumer, "consumer");
        requireText(operationId, "operationId");
        var at = referenceAt == null ? Instant.now() : referenceAt;

        var policy = policies.findByPurposeKeyAndActiveTrueOrderByPurposeVersionDesc(purpose).stream()
            .findFirst()
            .orElseThrow(() -> new ContextPlatformException("POLICY_NOT_FOUND", "Context purpose policy unavailable"));

        var existing = snapshots.findByUserIdAndPurposeKeyAndPurposeVersionAndOperationId(
            userId, purpose, policy.getPurposeVersion(), operationId
        ).orElse(null);
        if (existing != null) return existing;

        var policyJson = policy.getPolicyJson();
        var consumers = strings(policyJson.get("allowedConsumers"));
        if (!consumers.contains(consumer)) {
            throw new ContextPlatformException("FORBIDDEN", "Consumer is not allowed for context purpose");
        }

        var consentRequired = Boolean.TRUE.equals(policyJson.get("requiresConsent"));
        var allowedSensitivity = strings(policyJson.get("allowedSensitivityScopes"));
        var fieldPurpose = String.valueOf(policyJson.getOrDefault("fieldResolutionPurpose", purpose));
        var sources = sourceSpecs(policyJson.get("sources"));
        var material = new ArrayList<Map<String, Object>>();
        var decisions = new ArrayList<Map<String, Object>>();

        for (var source : sources) {
            if (!"CANONICAL_FIELD".equals(source.kind())) {
                decisions.add(decision(source, false, "UNSUPPORTED_SOURCE"));
                if (source.required()) {
                    throw new ContextPlatformException("DEPENDENCY_UNAVAILABLE", "Required context source type is unavailable");
                }
                continue;
            }

            try {
                var definition = fields.currentDefinition(source.sourceKey(), at);
                if (!allowedSensitivity.isEmpty()
                    && !allowedSensitivity.contains(definition.getSensitivityClass())) {
                    throw new ContextPlatformException("FORBIDDEN", "Source sensitivity is not allowed");
                }

                var guard = accessGuard.getIfAvailable();
                if (consentRequired && guard == null) {
                    throw new ContextPlatformException(
                        "DEPENDENCY_UNAVAILABLE",
                        "Privacy and consent guard is unavailable"
                    );
                }
                if (guard != null && !guard.allowed(
                    userId, definition.getSensitivityClass(), purpose, consentRequired
                )) {
                    throw new ContextPlatformException("CONSENT_REQUIRED", "Context source is not authorized");
                }

                var value = fields.resolveRelevantValue(
                    userId,
                    source.sourceKey(),
                    fieldPurpose,
                    at,
                    source.scopeType(),
                    source.scopeId()
                );
                material.add(material(value, definition.getSensitivityClass()));
                decisions.add(decision(source, true, "INCLUDED"));
            } catch (CanonicalFieldRegistryService.FieldRegistryException missing) {
                decisions.add(decision(source, false, "NO_VALUE_OR_AMBIGUOUS"));
                if (source.required()) {
                    throw new ContextPlatformException("VALIDATION_ERROR", "Required context source is unavailable");
                }
            }
        }

        var body = new TreeMap<String, Object>();
        body.put("subjectUserId", userId);
        body.put("purpose", purpose);
        body.put("purposeVersion", policy.getPurposeVersion());
        body.put("policySchemaVersion", policy.getPolicySchemaVersion());
        body.put("contextSchemaVersion", policy.getContextSchemaVersion());
        body.put("outputSchemaVersion", policy.getOutputSchemaVersion());
        body.put("consumer", consumer);
        body.put("referenceAt", at.toString());
        body.put("sources", material);
        body.put("decisions", decisions);

        var requestFingerprint = sha(String.join("|",
            userId, purpose, String.valueOf(policy.getPurposeVersion()), consumer, operationId, at.toString()
        ));
        var contentFingerprint = sha(canonicalJson(body));
        body.put("contentFingerprint", contentFingerprint);

        var entity = new ContextSnapshotEntity(
            UUID.randomUUID(),
            userId,
            purpose,
            policy.getPurposeVersion(),
            policy.getPolicySchemaVersion(),
            policy.getContextSchemaVersion(),
            policy.getOutputSchemaVersion(),
            operationId,
            requestFingerprint,
            contentFingerprint,
            at,
            Instant.now(),
            Map.copyOf(body)
        );

        try {
            return snapshots.saveAndFlush(entity);
        } catch (DataIntegrityViolationException race) {
            return snapshots.findByUserIdAndPurposeKeyAndPurposeVersionAndOperationId(
                userId, purpose, policy.getPurposeVersion(), operationId
            ).orElseThrow(() -> race);
        }
    }

    @Transactional(readOnly = true)
    public ContextSnapshotEntity get(UUID snapshotId, String userId) {
        var snapshot = snapshots.findById(snapshotId)
            .orElseThrow(() -> new ContextPlatformException("SNAPSHOT_NOT_FOUND", "Context snapshot not found"));
        if (!snapshot.getUserId().equals(userId)) {
            throw new ContextPlatformException("FORBIDDEN", "Context snapshot belongs to another user");
        }
        return snapshot;
    }

    private Map<String, Object> material(
        CanonicalFieldValueEntity value,
        String sensitivity
    ) {
        var out = new TreeMap<String, Object>();
        out.put("kind", "CANONICAL_FIELD");
        out.put("sourceKey", value.getFieldId());
        out.put("sourceId", value.getId());
        out.put("sourceVersion", value.getRecordVersion());
        out.put("definitionVersion", value.getDefinitionVersion());
        out.put("schemaVersion", value.getSchemaVersion());
        out.put("knowledgeState", value.getKnowledgeState());
        if (value.getTypedValue() != null) out.put("payload", value.getTypedValue());
        if (value.getReferenceId() != null) {
            out.put("reference", Map.of(
                "provider", value.getReferenceProvider(),
                "referenceId", value.getReferenceId()
            ));
        }
        out.put("sensitivityScope", sensitivity);
        out.put("recordedAt", value.getRecordedAt().toString());
        out.put("validFrom", value.getValidFrom().toString());
        if (value.getValidUntil() != null) out.put("validUntil", value.getValidUntil().toString());
        out.put("confidence", value.getConfidence());
        out.put("confirmedByUser", value.isConfirmedByUser());
        return Map.copyOf(out);
    }

    private Map<String, Object> decision(SourceSpec source, boolean included, String reason) {
        return Map.of(
            "kind", source.kind(),
            "sourceKey", source.sourceKey(),
            "included", included,
            "reason", reason
        );
    }

    @SuppressWarnings("unchecked")
    private List<SourceSpec> sourceSpecs(Object raw) {
        if (!(raw instanceof Collection<?> list)) return List.of();
        var out = new ArrayList<SourceSpec>();
        for (var item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            var kindValue = map.get("kind");
            var sourceKeyValue = map.get("sourceKey");
            out.add(new SourceSpec(
                kindValue == null ? "" : String.valueOf(kindValue),
                sourceKeyValue == null ? "" : String.valueOf(sourceKeyValue),
                Boolean.TRUE.equals(map.get("required")),
                nullable(map.get("scopeType")),
                nullable(map.get("scopeId"))
            ));
        }
        return List.copyOf(out);
    }

    private Set<String> strings(Object raw) {
        if (!(raw instanceof Collection<?> values)) return Set.of();
        return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String nullable(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String canonicalJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new ContextPlatformException("VALIDATION_ERROR", "Context snapshot cannot be canonicalized");
        }
    }

    private String sha(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            var out = new StringBuilder(64);
            for (byte item : bytes) out.append("%02x".formatted(item & 0xff));
            return out.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private record SourceSpec(
        String kind,
        String sourceKey,
        boolean required,
        String scopeType,
        String scopeId
    ) {}
}
