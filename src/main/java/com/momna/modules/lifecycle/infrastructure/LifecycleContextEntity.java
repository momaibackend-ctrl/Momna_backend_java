package com.momna.modules.lifecycle.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "lifecycle_contexts", schema = "momna")
public class LifecycleContextEntity {
    @Id
    private String id;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(name = "context_type", nullable = false)
    private String contextType;
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;
    @Column(name = "valid_to")
    private Instant validTo;
    @Column(nullable = false)
    private String source;
    @Column(nullable = false)
    private double confidence;

    protected LifecycleContextEntity() {}

    public LifecycleContextEntity(
        String id,
        String userId,
        String contextType,
        Instant validFrom,
        Instant validTo,
        String source,
        double confidence
    ) {
        this.id = id;
        this.userId = userId;
        this.contextType = contextType;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.source = source;
        this.confidence = confidence;
    }

    public void closeAt(Instant at) {
        if (validTo == null || at.isBefore(validTo)) {
            this.validTo = at;
        }
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getContextType() { return contextType; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public String getSource() { return source; }
    public double getConfidence() { return confidence; }
}
