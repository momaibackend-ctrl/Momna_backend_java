package com.momna.modules.checkin.infrastructure;

import com.momna.modules.checkin.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "checkin_sessions",
    schema = "momna",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "phase", "local_date", "period_at_time"})
)
public class CheckinSessionEntity {
    @Id
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckinPhase phase;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "period_at_time", nullable = false)
    private String periodAtTime;

    @Column(name = "lifecycle_substage_at_time")
    private String lifecycleSubstageAtTime;

    @Column(name = "timezone_at_session", nullable = false)
    private String timezoneAtSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckinSessionStatus status;

    @Version
    @Column(nullable = false)
    private long revision;

    @Column(name = "definition_version", nullable = false)
    private int definitionVersion;

    @Column(name = "rules_version", nullable = false)
    private String rulesVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applicable_item_codes", nullable = false, columnDefinition = "jsonb")
    private List<String> applicableItemCodes = new ArrayList<>();

    @Column(name = "completion_ratio", nullable = false)
    private BigDecimal completionRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "safety_state", nullable = false)
    private CheckinSafetyState safetyState;

    @Column(name = "safety_decision_version")
    private String safetyDecisionVersion;

    @Column(name = "safety_route_code")
    private String safetyRouteCode;

    @Column(name = "safety_severity")
    private String safetySeverity;

    @Column(name = "safety_blocking", nullable = false)
    private boolean safetyBlocking;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safety_clarification", columnDefinition = "jsonb")
    private Map<String, Object> safetyClarification;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safety_allowed_actions", nullable = false, columnDefinition = "jsonb")
    private List<String> safetyAllowedActions = new ArrayList<>();

    @Column(name = "window_opens_at", nullable = false)
    private Instant windowOpensAt;

    @Column(name = "window_closes_at", nullable = false)
    private Instant windowClosesAt;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "finalization_reason")
    private CheckinFinalizationReason finalizationReason;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "consumed_by_type")
    private String consumedByType;

    @Column(name = "consumed_by_ref")
    private String consumedByRef;

    protected CheckinSessionEntity() {}

    public CheckinSessionEntity(
        String sessionId,
        String userId,
        CheckinPhase phase,
        LocalDate localDate,
        String periodAtTime,
        String lifecycleSubstageAtTime,
        String timezoneAtSession,
        int definitionVersion,
        String rulesVersion,
        Collection<String> applicableItemCodes,
        Instant windowOpensAt,
        Instant windowClosesAt,
        Instant now
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.phase = phase;
        this.localDate = localDate;
        this.periodAtTime = periodAtTime;
        this.lifecycleSubstageAtTime = lifecycleSubstageAtTime;
        this.timezoneAtSession = timezoneAtSession;
        this.status = CheckinSessionStatus.DRAFT;
        this.definitionVersion = definitionVersion;
        this.rulesVersion = rulesVersion;
        this.applicableItemCodes = new ArrayList<>(applicableItemCodes);
        this.completionRatio = BigDecimal.ZERO;
        this.safetyState = CheckinSafetyState.CLEAR;
        this.safetyBlocking = false;
        this.windowOpensAt = windowOpensAt;
        this.windowClosesAt = windowClosesAt;
        this.startedAt = now;
        this.updatedAt = now;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public CheckinPhase getPhase() { return phase; }
    public LocalDate getLocalDate() { return localDate; }
    public String getPeriodAtTime() { return periodAtTime; }
    public String getLifecycleSubstageAtTime() { return lifecycleSubstageAtTime; }
    public String getTimezoneAtSession() { return timezoneAtSession; }
    public CheckinSessionStatus getStatus() { return status; }
    public long getRevision() { return revision; }
    public int getDefinitionVersion() { return definitionVersion; }
    public String getRulesVersion() { return rulesVersion; }
    public List<String> getApplicableItemCodes() { return List.copyOf(applicableItemCodes); }
    public BigDecimal getCompletionRatio() { return completionRatio; }
    public CheckinSafetyState getSafetyState() { return safetyState; }
    public boolean isSafetyBlocking() { return safetyBlocking; }
    public Instant getWindowOpensAt() { return windowOpensAt; }
    public Instant getWindowClosesAt() { return windowClosesAt; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public CheckinFinalizationReason getFinalizationReason() { return finalizationReason; }
    public Instant getConsumedAt() { return consumedAt; }
    public String getConsumedByRef() { return consumedByRef; }

    public void updateCompletion(int answered, Instant at) {
        this.completionRatio = applicableItemCodes.isEmpty()
            ? BigDecimal.ZERO
            : BigDecimal.valueOf((double) answered / applicableItemCodes.size());
        this.status = answered == 0 ? CheckinSessionStatus.DRAFT : CheckinSessionStatus.PARTIAL;
        this.updatedAt = at;
    }

    public void finalizeByUser(Instant at) {
        this.status = CheckinSessionStatus.SUBMITTED;
        this.submittedAt = at;
        this.finalizedAt = at;
        this.finalizationReason = CheckinFinalizationReason.USER_SUBMIT;
        this.updatedAt = at;
    }

    public void autoFinalize(Instant at) {
        this.status = CheckinSessionStatus.AUTO_FINALIZED;
        this.finalizedAt = at;
        this.finalizationReason = CheckinFinalizationReason.WINDOW_CLOSED;
        this.updatedAt = at;
    }
}
