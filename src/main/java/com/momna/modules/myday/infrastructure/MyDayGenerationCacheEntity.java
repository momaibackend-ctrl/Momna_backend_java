package com.momna.modules.myday.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A verified generation result reusable across users; never a user's own day. */
@Entity
@Table(name = "my_day_generation_cache", schema = "momna")
public class MyDayGenerationCacheEntity {
    @Id
    @Column(name = "cache_key")
    private String cacheKey;

    @Column(name = "life_mode", nullable = false)
    private String lifeMode;

    @Column(nullable = false)
    private String language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> cards;

    @Column(name = "card_source", nullable = false) private String cardSource;
    @Column(name = "generation_policy_version", nullable = false) private String generationPolicyVersion;
    @Column(name = "prompt_version") private String promptVersion;
    @Column(name = "model_ref") private String modelRef;
    @Column(name = "safety_version") private String safetyVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    protected MyDayGenerationCacheEntity() {}

    public MyDayGenerationCacheEntity(String cacheKey, String lifeMode, String language,
                                      List<Map<String, Object>> cards, String cardSource,
                                      String generationPolicyVersion, String promptVersion, String modelRef,
                                      String safetyVersion, Instant createdAt, Instant expiresAt) {
        this.cacheKey = cacheKey;
        this.lifeMode = lifeMode;
        this.language = language;
        this.cards = List.copyOf(cards);
        this.cardSource = cardSource;
        this.generationPolicyVersion = generationPolicyVersion;
        this.promptVersion = promptVersion;
        this.modelRef = modelRef;
        this.safetyVersion = safetyVersion;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getCacheKey() { return cacheKey; }
    public String getLifeMode() { return lifeMode; }
    public String getLanguage() { return language; }
    public List<Map<String, Object>> getCards() { return cards; }
    public String getCardSource() { return cardSource; }
    public String getGenerationPolicyVersion() { return generationPolicyVersion; }
    public String getPromptVersion() { return promptVersion; }
    public String getModelRef() { return modelRef; }
    public String getSafetyVersion() { return safetyVersion; }
    public Instant getExpiresAt() { return expiresAt; }
}
