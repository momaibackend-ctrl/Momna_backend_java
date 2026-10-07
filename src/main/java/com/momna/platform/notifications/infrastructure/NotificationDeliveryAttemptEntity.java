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

    public UUID getAttemptId() { return attemptId; }
    public UUID getScheduleId() { return scheduleId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public int getAttemptNumber() { return attemptNumber; }
    public DeliveryAttemptStatus getStatus() { return status; }
    public Instant getNextRetryAt() { return nextRetryAt; }
}
