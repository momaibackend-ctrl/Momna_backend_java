package com.momna.core.fields.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "canonical_field_values", schema = "momna")
public class CanonicalFieldValueEntity {
    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "field_id", nullable = false)
    private String fieldId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "typed_value", columnDefinition = "jsonb")
    private Map<String, Object> typedValue;

    @Column(name = "reference_provider")
    private String referenceProvider;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "flow_instance_id")
    private String flowInstanceId;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    @Column(name = "scope_id")
    private String scopeId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "confirmed_by_user", nullable = false)
    private boolean confirmedByUser;

    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;

    @Column(name = "definition_version", nullable = false)
    private int definitionVersion;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "knowledge_state", nullable = false)
    private String knowledgeState;

    @Column(name = "privacy_classification")
    private String privacyClassification;

    @Column(name = "consent_scope")
    private String consentScope;

    @Column(name = "sensitivity_classification")
    private String sensitivityClassification;

    @Column(name = "applied_merge_policy")
    private String appliedMergePolicy;

    protected CanonicalFieldValueEntity() {}

    public CanonicalFieldValueEntity(
        String id,
        String userId,
        String fieldId,
        Map<String, Object> typedValue,
        String referenceProvider,
        String referenceId,
        String sourceType,
        String sourceId,
        String flowInstanceId,
        String purpose,
        String scopeType,
        String scopeId,
        Instant recordedAt,
        Instant validFrom,
        Instant validUntil,
        BigDecimal confidence,
        boolean confirmedByUser,
        int definitionVersion,
        int schemaVersion,
        String knowledgeState,
        String privacyClassification,
        String consentScope,
        String sensitivityClassification,
        String appliedMergePolicy
    ) {
        this.id = id;
        this.userId = userId;
        this.fieldId = fieldId;
        this.typedValue = typedValue;
        this.referenceProvider = referenceProvider;
        this.referenceId = referenceId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.flowInstanceId = flowInstanceId;
        this.purpose = purpose;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.recordedAt = recordedAt;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.confidence = confidence;
        this.confirmedByUser = confirmedByUser;
        this.definitionVersion = definitionVersion;
        this.schemaVersion = schemaVersion;
        this.knowledgeState = knowledgeState;
        this.privacyClassification = privacyClassification;
        this.consentScope = consentScope;
        this.sensitivityClassification = sensitivityClassification;
        this.appliedMergePolicy = appliedMergePolicy;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getFieldId() { return fieldId; }
    public Map<String, Object> getTypedValue() { return typedValue; }
    public String getReferenceProvider() { return referenceProvider; }
    public String getReferenceId() { return referenceId; }
    public String getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public String getFlowInstanceId() { return flowInstanceId; }
    public String getPurpose() { return purpose; }
    public String getScopeType() { return scopeType; }
    public String getScopeId() { return scopeId; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public BigDecimal getConfidence() { return confidence; }
    public boolean isConfirmedByUser() { return confirmedByUser; }
    public long getRecordVersion() { return recordVersion; }
    public int getDefinitionVersion() { return definitionVersion; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getKnowledgeState() { return knowledgeState; }
    public String getPrivacyClassification() { return privacyClassification; }
    public String getConsentScope() { return consentScope; }
    public String getSensitivityClassification() { return sensitivityClassification; }
    public String getAppliedMergePolicy() { return appliedMergePolicy; }
}
