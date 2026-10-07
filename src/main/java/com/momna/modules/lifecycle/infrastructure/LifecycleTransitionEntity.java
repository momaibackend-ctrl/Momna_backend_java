package com.momna.modules.lifecycle.infrastructure;

import com.momna.modules.lifecycle.domain.LifecyclePeriod;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "lifecycle_transitions", schema = "momna")
public class LifecycleTransitionEntity {
    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_period")
    private LifecyclePeriod fromPeriod;

    @Column(name = "from_substage")
    private String fromSubstage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_period", nullable = false)
    private LifecyclePeriod toPeriod;

    @Column(name = "to_substage")
    private String toSubstage;

    @Column(nullable = false)
    private String reason;

    @Column(name = "confirmation_state", nullable = false)
    private String confirmationState;

    @Column(name = "rule_version", nullable = false)
    private String ruleVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "selected_manually", nullable = false)
    private boolean selectedManually;

    protected LifecycleTransitionEntity() {}

    public LifecycleTransitionEntity(
        String id,
        String userId,
        LifecyclePeriod fromPeriod,
        String fromSubstage,
        LifecyclePeriod toPeriod,
        String toSubstage,
        String reason,
        String confirmationState,
        String ruleVersion,
        Instant occurredAt,
        String source,
        double confidence,
        boolean selectedManually
    ) {
        this.id = id;
        this.userId = userId;
        this.fromPeriod = fromPeriod;
        this.fromSubstage = fromSubstage;
        this.toPeriod = toPeriod;
        this.toSubstage = toSubstage;
        this.reason = reason;
        this.confirmationState = confirmationState;
        this.ruleVersion = ruleVersion;
        this.occurredAt = occurredAt;
        this.source = source;
        this.confidence = confidence;
        this.selectedManually = selectedManually;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public LifecyclePeriod getFromPeriod() { return fromPeriod; }
    public String getFromSubstage() { return fromSubstage; }
    public LifecyclePeriod getToPeriod() { return toPeriod; }
    public String getToSubstage() { return toSubstage; }
    public String getReason() { return reason; }
    public String getConfirmationState() { return confirmationState; }
    public String getRuleVersion() { return ruleVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getSource() { return source; }
    public double getConfidence() { return confidence; }
    public boolean isSelectedManually() { return selectedManually; }

    public void confirm() {
        this.confirmationState = "CONFIRMED";
    }
}
