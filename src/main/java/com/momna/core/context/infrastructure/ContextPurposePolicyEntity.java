package com.momna.core.context.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "context_purpose_policies", schema = "momna")
@IdClass(ContextPurposePolicyId.class)
public class ContextPurposePolicyEntity {
    @Id
    @Column(name = "purpose_key")
    private String purposeKey;

    @Id
    @Column(name = "purpose_version")
    private int purposeVersion;

    @Column(name = "policy_schema_version", nullable = false)
    private int policySchemaVersion;

    @Column(name = "context_schema_version", nullable = false)
    private int contextSchemaVersion;

    @Column(name = "output_schema_version", nullable = false)
    private int outputSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> policyJson;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(nullable = false)
    private boolean active;

    protected ContextPurposePolicyEntity() {}

    public String getPurposeKey() { return purposeKey; }
    public int getPurposeVersion() { return purposeVersion; }
    public int getPolicySchemaVersion() { return policySchemaVersion; }
    public int getContextSchemaVersion() { return contextSchemaVersion; }
    public int getOutputSchemaVersion() { return outputSchemaVersion; }
    public Map<String, Object> getPolicyJson() { return policyJson; }
    public Instant getPublishedAt() { return publishedAt; }
    public boolean isActive() { return active; }
}
