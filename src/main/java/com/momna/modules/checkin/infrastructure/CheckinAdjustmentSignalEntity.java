package com.momna.modules.checkin.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "checkin_adjustment_signals", schema = "momna")
public class CheckinAdjustmentSignalEntity {
    @Id
    @Column(name = "signal_id", length = 64)
    private String signalId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "signal_code", nullable = false)
    private String signalCode;

    @Column(name = "source_phase", nullable = false)
    private String sourcePhase;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "period_at_time", nullable = false)
    private String periodAtTime;

    @Column(name = "state_points", nullable = false)
    private int statePoints;

    @Column(nullable = false)
    private String tier;

    @Column(name = "score_contribution", nullable = false)
    private BigDecimal scoreContribution;

    @Column(name = "completion_ratio", nullable = false)
    private BigDecimal completionRatio;

    @Column(name = "effective_phase_weight")
    private BigDecimal effectivePhaseWeight;

    @Column(name = "rules_version", nullable = false)
    private String rulesVersion;

    @Column(name = "definition_version", nullable = false)
    private int definitionVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheckinAdjustmentSignalEntity() {}

    public CheckinAdjustmentSignalEntity(
        String signalId,
        String userId,
        String sessionId,
        String itemCode,
        String signalCode,
        String sourcePhase,
        LocalDate localDate,
        String periodAtTime,
        int statePoints,
        String tier,
        BigDecimal scoreContribution,
        BigDecimal completionRatio,
        String rulesVersion,
        int definitionVersion,
        Instant createdAt
    ) {
        this.signalId = signalId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.itemCode = itemCode;
        this.signalCode = signalCode;
        this.sourcePhase = sourcePhase;
        this.localDate = localDate;
        this.periodAtTime = periodAtTime;
        this.statePoints = statePoints;
        this.tier = tier;
        this.scoreContribution = scoreContribution;
        this.completionRatio = completionRatio;
        this.rulesVersion = rulesVersion;
        this.definitionVersion = definitionVersion;
        this.createdAt = createdAt;
    }

    public String getSignalId() { return signalId; }
    public String getSessionId() { return sessionId; }
    public String getItemCode() { return itemCode; }
    public String getSignalCode() { return signalCode; }
    public String getSourcePhase() { return sourcePhase; }
    public String getTier() { return tier; }
    public BigDecimal getScoreContribution() { return scoreContribution; }
    public BigDecimal getCompletionRatio() { return completionRatio; }
    public BigDecimal getEffectivePhaseWeight() { return effectivePhaseWeight; }
    public String getRulesVersion() { return rulesVersion; }
    public int getDefinitionVersion() { return definitionVersion; }
}
