package com.momna.platform.notifications.infrastructure;

import com.momna.platform.notifications.DeliveryAttemptStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_delivery_attempts", schema = "momna")
public class NotificationDeliveryAttemptEntity {
    @Id
    @Column(name = "attempt_id")
    private UUID attemptId;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryAttemptStatus status;

    @Column(name = "content_id", nullable = false)
    private String contentId;

    @Column(name = "content_key", nullable = false)
    private String contentKey;

    @Column(name = "content_version", nullable = false)
    private int contentVersion;

    @Column(name = "content_schema_version", nullable = false)
    private int contentSchemaVersion;

    @Column(name = "resolved_locale", nullable = false)
    private String resolvedLocale;

    @Column(name = "locale_policy_version", nullable = false)
    private String localePolicyVersion;

    @Column(name = "country_policy_version")
    private String countryPolicyVersion;

    @Column(name = "retry_policy_version", nullable = false)
    private String retryPolicyVersion;

    @Column(name = "provider_neutral_code")
    private String providerNeutralCode;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationDeliveryAttemptEntity() {}

    public NotificationDeliveryAttemptEntity(
        UUID attemptId,
        UUID scheduleId,
        Instant scheduledAt,
        int attemptNumber,
        DeliveryAttemptStatus status,
        String contentId,
        String contentKey,
        int contentVersion,
        int contentSchemaVersion,
        String resolvedLocale,
        String localePolicyVersion,
        String countryPolicyVersion,
        String retryPolicyVersion,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.attemptId = attemptId;
        this.scheduleId = scheduleId;
        this.scheduledAt = scheduledAt;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.contentId = contentId;
        this.contentKey = contentKey;
        this.contentVersion = contentVersion;
        this.contentSchemaVersion = contentSchemaVersion;
        this.resolvedLocale = resolvedLocale;
        this.localePolicyVersion = localePolicyVersion;
        this.countryPolicyVersion = countryPolicyVersion;
        this.retryPolicyVersion = retryPolicyVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void markEnqueued(Instant at) {
        this.status = DeliveryAttemptStatus.ENQUEUED;
        this.updatedAt = at;
    }

    public void markDelivered(String code, Instant at) {
        this.status = DeliveryAttemptStatus.DELIVERED;
        this.providerNeutralCode = code;
        this.nextRetryAt = null;
        this.updatedAt = at;
    }

    public void markRetry(String code, Instant retryAt, Instant at) {
        this.status = DeliveryAttemptStatus.RETRY_WAIT;
        this.providerNeutralCode = code;
        this.nextRetryAt = retryAt;
        this.updatedAt = at;
    }

    public void markFailed(String code, boolean deadLetter, Instant at) {
        this.status = deadLetter ? DeliveryAttemptStatus.DEAD_LETTER : DeliveryAttemptStatus.FAILED;
        this.providerNeutralCode = code;
        this.nextRetryAt = null;
        this.updatedAt = at;
    }

    public UUID getAttemptId() { return attemptId; }
    public UUID getScheduleId() { return scheduleId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public int getAttemptNumber() { return attemptNumber; }
    public DeliveryAttemptStatus getStatus() { return status; }
    public String getContentId() { return contentId; }
    public String getContentKey() { return contentKey; }
    public int getContentVersion() { return contentVersion; }
    public int getContentSchemaVersion() { return contentSchemaVersion; }
    public String getResolvedLocale() { return resolvedLocale; }
    public String getLocalePolicyVersion() { return localePolicyVersion; }
    public String getCountryPolicyVersion() { return countryPolicyVersion; }
    public String getRetryPolicyVersion() { return retryPolicyVersion; }
    public String getProviderNeutralCode() { return providerNeutralCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
}
