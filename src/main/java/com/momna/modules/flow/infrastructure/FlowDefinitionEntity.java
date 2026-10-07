package com.momna.modules.flow.infrastructure;

import com.momna.modules.flow.domain.FlowType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "flow_definitions", schema = "momna")
@IdClass(FlowDefinitionId.class)
public class FlowDefinitionEntity {
    @Id
    @Column(name = "definition_key")
    private String definitionKey;

    @Id
    @Column(name = "definition_version")
    private int definitionVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false)
    private FlowType flowType;

    private String variant;
    private String period;
    private String substage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> definitionJson;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected FlowDefinitionEntity() {}

    public String getDefinitionKey() { return definitionKey; }
    public int getDefinitionVersion() { return definitionVersion; }
    public FlowType getFlowType() { return flowType; }
    public String getVariant() { return variant; }
    public String getPeriod() { return period; }
    public String getSubstage() { return substage; }
    public Map<String, Object> getDefinitionJson() { return definitionJson; }
    public int getSchemaVersion() { return schemaVersion; }
    public Instant getPublishedAt() { return publishedAt; }
}
