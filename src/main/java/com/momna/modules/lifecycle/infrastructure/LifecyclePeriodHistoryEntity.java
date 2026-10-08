package com.momna.modules.lifecycle.infrastructure;

import com.momna.modules.lifecycle.domain.LifecyclePeriod;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "lifecycle_period_history", schema = "momna")
public class LifecyclePeriodHistoryEntity {
    @Id
    private String id;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LifecyclePeriod period;
    private String substage;
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;
    @Column(name = "effective_to")
    private Instant effectiveTo;
    @Column(nullable = false)
    private String source;
    @Column(nullable = false)
    private double confidence;
    @Column(name = "selected_manually", nullable = false)
    private boolean selectedManually;

    protected LifecyclePeriodHistoryEntity() {}

    public LifecyclePeriodHistoryEntity(
        String id,
        String userId,
        LifecyclePeriod period,
        String substage,
        Instant effectiveFrom,
        Instant effectiveTo,
        String source,
        double confidence,
        boolean selectedManually
    ) {
        this.id = id;
        this.userId = userId;
        this.period = period;
        this.substage = substage;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.source = source;
        this.confidence = confidence;
        this.selectedManually = selectedManually;
    }

    public void closeAt(Instant at) {
        if (effectiveTo == null || at.isBefore(effectiveTo)) {
            this.effectiveTo = at;
        }
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public LifecyclePeriod getPeriod() { return period; }
    public String getSubstage() { return substage; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
    public String getSource() { return source; }
    public double getConfidence() { return confidence; }
    public boolean isSelectedManually() { return selectedManually; }
}
