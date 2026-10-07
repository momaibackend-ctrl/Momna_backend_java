package com.momna.core.fields.application;

import com.momna.core.fields.infrastructure.*;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CanonicalFieldRegistryService {
    private final CanonicalFieldDefinitionRepository definitions;
    private final CanonicalFieldValueRepository values;

    public CanonicalFieldRegistryService(
        CanonicalFieldDefinitionRepository definitions,
        CanonicalFieldValueRepository values
    ) {
        this.definitions = definitions;
        this.values = values;
    }

    public CanonicalFieldDefinitionEntity currentDefinition(String fieldId, Instant at) {
        requireText(fieldId, "fieldId");
        var referenceAt = at == null ? Instant.now() : at;
        return definitions.findByFieldIdOrderByDefinitionVersionDesc(fieldId).stream()
            .filter(x -> x.getDeprecatedAt() == null || x.getDeprecatedAt().isAfter(referenceAt))
            .findFirst()
            .orElseThrow(() -> new FieldRegistryException("FIELD_NOT_FOUND", "Canonical field definition unavailable"));
    }

    public CanonicalFieldDefinitionEntity exactDefinition(String fieldId, int definitionVersion) {
        requireText(fieldId, "fieldId");
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("definitionVersion must be positive");
        }
        return definitions.findById(new CanonicalFieldDefinitionId(fieldId, definitionVersion))
            .orElseThrow(() -> new FieldRegistryException("FIELD_NOT_FOUND", "Canonical field definition version unavailable"));
    }

    public List<CanonicalFieldDefinitionEntity> listDefinitions(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return definitions.findAll().stream()
                .sorted(java.util.Comparator
                    .comparing(CanonicalFieldDefinitionEntity::getFieldId)
                    .thenComparing(CanonicalFieldDefinitionEntity::getDefinitionVersion).reversed())
                .toList();
        }
        return definitions.findByNamespaceOrderByFieldIdAscDefinitionVersionDesc(namespace.trim());
    }

    public List<CanonicalFieldValueEntity> history(String userId, String fieldId) {
        requireText(userId, "userId");
        requireText(fieldId, "fieldId");
        return values.findByUserIdAndFieldIdOrderByRecordedAtDesc(userId, fieldId);
    }

    public CanonicalFieldValueEntity resolveRelevantValue(
        String userId,
        String fieldId,
        String purpose,
        Instant at,
        String scopeType,
        String scopeId
    ) {
        requireText(userId, "userId");
        requireText(fieldId, "fieldId");
        requireText(purpose, "purpose");
        var referenceAt = at == null ? Instant.now() : at;

        var candidates = values.findByUserIdAndFieldIdOrderByRecordedAtDesc(userId, fieldId).stream()
            .filter(x -> purpose.equals(x.getPurpose()))
            .filter(x -> !x.getValidFrom().isAfter(referenceAt))
            .filter(x -> x.getValidUntil() == null || referenceAt.isBefore(x.getValidUntil()))
            .filter(x -> scopeType == null || scopeType.equals(x.getScopeType()))
            .filter(x -> scopeId == null || scopeId.equals(x.getScopeId()))
            .toList();

        if (candidates.isEmpty()) {
            throw new FieldRegistryException("AMBIGUOUS_OR_STALE", "No current canonical field value");
        }

        var first = candidates.getFirst();
        if (candidates.size() > 1
            && candidates.get(1).getRecordedAt().equals(first.getRecordedAt())
            && candidates.get(1).getConfidence().compareTo(first.getConfidence()) == 0) {
            throw new FieldRegistryException("AMBIGUOUS_OR_STALE", "Canonical field value is ambiguous");
        }

        exactDefinition(first.getFieldId(), first.getDefinitionVersion());
        return first;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    public static class FieldRegistryException extends RuntimeException {
        private final String code;

        public FieldRegistryException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() { return code; }
    }
}
