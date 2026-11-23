package com.momna.modules.myday.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "my_days", schema = "momna")
public class MyDayEntity {
    @Id
    @Column(name = "my_day_id")
    private String myDayId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "timezone_at_generation", nullable = false)
    private String timezoneAtGeneration;

    @Column(name = "lifecycle_period", nullable = false)
    private String lifecyclePeriod;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private String status;

    @Version
    @Column(nullable = false)
    private long revision;

    @Column(name = "orchestration_id", nullable = false)
    private String orchestrationId;

    @Column(name = "orchestration_version", nullable = false)
    private String orchestrationVersion;

    @Column(name = "baseline_share", nullable = false, precision = 8, scale = 6)
    private BigDecimal baselineShare;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> provenance;

    @Column(name = "card_source", nullable = false)
    private String cardSource;

    @Column(name = "generation_policy_version", nullable = false)
    private String generationPolicyVersion;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "model_ref")
    private String modelRef;

    @Column(name = "safety_version")
    private String safetyVersion;

    @Column(name = "cache_key")
    private String cacheKey;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MyDayEntity() {}

    public MyDayEntity(
        String myDayId, String userId, LocalDate localDate, String timezoneAtGeneration,
        String lifecyclePeriod, String language, String orchestrationId, String orchestrationVersion,
        BigDecimal baselineShare, Map<String, Object> provenance, String cardSource,
        String generationPolicyVersion, String promptVersion, String modelRef, String safetyVersion,
        String cacheKey, Instant at
    ) {
        this.myDayId = myDayId;
        this.userId = userId;
        this.localDate = localDate;
        this.timezoneAtGeneration = timezoneAtGeneration;
        this.lifecyclePeriod = lifecyclePeriod;
        this.language = language;
        this.status = "READY";
        this.orchestrationId = orchestrationId;
        this.orchestrationVersion = orchestrationVersion;
        this.baselineShare = baselineShare;
        this.provenance = new LinkedHashMap<>(provenance);
        this.cardSource = cardSource;
        this.generationPolicyVersion = generationPolicyVersion;
        this.promptVersion = promptVersion;
        this.modelRef = modelRef;
        this.safetyVersion = safetyVersion;
        this.cacheKey = cacheKey;
        this.generatedAt = at;
        this.updatedAt = at;
    }

    /** A later Check-in (for example Morning(D) after Evening(D-1)) updates provenance only. */
    public void recordOrchestration(String orchestrationId, String orchestrationVersion,
                                    BigDecimal baselineShare, Map<String, Object> provenance, Instant at) {
        this.orchestrationId = orchestrationId;
        this.orchestrationVersion = orchestrationVersion;
        this.baselineShare = baselineShare;
        this.provenance = new LinkedHashMap<>(provenance);
        this.updatedAt = at;
    }

    public void touch(Instant at) { this.updatedAt = at; }

    /** Served from a similar verified day because generation was unavailable. */
    public void markDegraded() { this.status = "DEGRADED_READY"; }

    public String getMyDayId() { return myDayId; }
    public String getUserId() { return userId; }
    public LocalDate getLocalDate() { return localDate; }
    public String getTimezoneAtGeneration() { return timezoneAtGeneration; }
    public String getLifecyclePeriod() { return lifecyclePeriod; }
    public String getLanguage() { return language; }
    public String getStatus() { return status; }
    public long getRevision() { return revision; }
    public String getOrchestrationId() { return orchestrationId; }
    public String getOrchestrationVersion() { return orchestrationVersion; }
    public BigDecimal getBaselineShare() { return baselineShare; }
    public Map<String, Object> getProvenance() { return provenance == null ? Map.of() : Map.copyOf(provenance); }
    public String getCardSource() { return cardSource; }
    public String getGenerationPolicyVersion() { return generationPolicyVersion; }
    public String getPromptVersion() { return promptVersion; }
    public String getModelRef() { return modelRef; }
    public String getSafetyVersion() { return safetyVersion; }
    public String getCacheKey() { return cacheKey; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
