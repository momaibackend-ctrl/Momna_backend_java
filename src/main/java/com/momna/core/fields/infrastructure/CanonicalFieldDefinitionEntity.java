package com.momna.core.fields.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "canonical_field_definition_versions", schema = "momna")
@IdClass(CanonicalFieldDefinitionId.class)
public class CanonicalFieldDefinitionEntity {
    @Id
    @Column(name = "field_id")
    private String fieldId;

    @Id
    @Column(name = "definition_version")
    private int definitionVersion;

    @Column(nullable = false)
    private String namespace;

    @Column(name = "data_type", nullable = false)
    private String dataType;

    @Column(name = "domain_owner", nullable = false)
    private String domainOwner;

    @Column(name = "sensitivity_class", nullable = false)
    private String sensitivityClass;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_schema", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> validationSchema;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "registry_policy", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> registryPolicy;

    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CanonicalFieldDefinitionEntity() {}

    public String getFieldId() { return fieldId; }
    public int getDefinitionVersion() { return definitionVersion; }
    public String getNamespace() { return namespace; }
    public String getDataType() { return dataType; }
    public String getDomainOwner() { return domainOwner; }
    public String getSensitivityClass() { return sensitivityClass; }
    public Map<String, Object> getValidationSchema() { return validationSchema; }
    public int getSchemaVersion() { return schemaVersion; }
    public Map<String, Object> getRegistryPolicy() { return registryPolicy; }
    public Instant getDeprecatedAt() { return deprecatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
