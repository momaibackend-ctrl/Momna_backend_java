package com.momna.platform.safety;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One durable route state per user and route, independent of check-in windows. */
@Entity
@Table(name = "medical_safety_route_states", schema = "momna",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "route_id"}))
public class MedicalSafetyRouteStateEntity {
    @Id
    @Column(name = "route_state_id")
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(name = "route_id", nullable = false)
    private String routeId;
    @Column(name = "session_id")
    private String sessionId;
    @Column(name = "source_item")
    private String sourceItem;
    @Column(name = "state", nullable = false)
    private String state = "QUESTION";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fact_answers", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> factAnswers = new LinkedHashMap<>();
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "cooldown_until")
    private Instant cooldownUntil;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "reset_observed", nullable = false)
    private boolean resetObserved;
    @Column(name = "disposition")
    private String disposition;
    @Column(name = "screen_code")
    private String screenCode;
    @Column(name = "action_code")
    private String actionCode;
    @Column(name = "bypass_reason")
    private String bypassReason;
    @Column(name = "catalog_version", nullable = false)
    private String catalogVersion = MedicalSafetyCatalog.VERSION;
    @Version
    @Column(name = "revision", nullable = false)
    private long revision;

    protected MedicalSafetyRouteStateEntity() {}

    public MedicalSafetyRouteStateEntity(String userId, String routeId, Instant now) {
        this.id = UUID.nameUUIDFromBytes((userId + ":" + routeId)
            .getBytes(StandardCharsets.UTF_8)).toString();
        this.userId = userId;
        this.routeId = routeId;
        this.updatedAt = now;
    }
    public String getUserId() { return userId; }
    public String getRouteId() { return routeId; }
    public String getSessionId() { return sessionId; }
    public String getState() { return state; }
    public String getSourceItem() { return sourceItem; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCooldownUntil() { return cooldownUntil; }
    public String getDisposition() { return disposition; }
    public String getScreenCode() { return screenCode; }
    public String getActionCode() { return actionCode; }
    public long getRevision() { return revision; }
    public Map<String, String> getFactAnswers() { return Map.copyOf(factAnswers); }
    public boolean getResetObserved() { return resetObserved; }

    public void observeRecovery(String itemCode, Instant now) {
        if (Objects.equals(sourceItem, itemCode) && !factAnswers.containsValue("YES")) {
            resetObserved = true;
            updatedAt = now;
        }
    }

    public boolean mayStart(String itemCode, String currentSessionId, Instant now) {
        if ("QUESTION".equals(state) && Objects.equals(sessionId, currentSessionId)) return false;
        if ("QUESTION".equals(state)) return true;
        if (completedAt == null) return true;
        if (factAnswers.containsValue("YES")) return false; // preserve active warning
        return cooldownUntil == null || !now.isBefore(cooldownUntil)
            || resetObserved || !Objects.equals(sourceItem, itemCode);
    }

    public void begin(String sessionId, String sourceItem, Instant at) {
        this.bypassReason = resetObserved ? "RECOVERED_THEN_WORSENED"
            : this.sourceItem != null && !this.sourceItem.equals(sourceItem) ? "NEW_ITEM" : "NONE";
        this.sessionId = sessionId;
        this.sourceItem = sourceItem;
        this.state = "QUESTION";
        this.factAnswers.entrySet().removeIf(entry -> !"YES".equals(entry.getValue()));
        this.resetObserved = false;
        this.completedAt = null;
        this.cooldownUntil = null;
        this.disposition = null;
        this.screenCode = null;
        this.actionCode = null;
        this.updatedAt = at;
    }

    public void answer(String factCode, String value, Instant now) {
        if (!Set.of("YES", "NO", "UNSURE", "ABSENT").contains(value)) {
            throw new IllegalArgumentException("Invalid safety answer");
        }
        factAnswers.put(factCode, value);
        updatedAt = now;
    }

    public void recordBloodPressure(Integer systolic, Integer diastolic, boolean unknown, Instant at) {
        if (!unknown && (systolic == null || diastolic == null || systolic <= 0
            || diastolic <= 0 || systolic > 500 || diastolic > 500)) {
            throw new IllegalArgumentException("Both positive measured pressure values are required");
        }
        factAnswers.put("BP_READING", unknown ? "UNKNOWN" : systolic + "/" + diastolic);
        this.updatedAt = at;
    }

    public void update(com.momna.platform.safety.CanonicalSafetyService.MedicalStep result,
        Instant now, int cooldownHours) {
        this.state = result.state();
        this.disposition = result.disposition();
        this.screenCode = result.screenCode();
        this.actionCode = result.actionCode();
        this.updatedAt = now;
        if ("COMPLETE".equals(result.state())) {
            if (completedAt == null) completedAt = now;
            cooldownUntil = factAnswers.containsValue("YES") || cooldownHours == 0
                ? null : now.plus(java.time.Duration.ofHours(cooldownHours));
        }
    }
}
