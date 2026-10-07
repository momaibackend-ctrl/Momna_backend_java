package com.momna.platform.notifications.infrastructure;

import com.momna.platform.notifications.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "notification_schedules", schema = "momna")
public class NotificationScheduleEntity {
    @Id
    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "content_key", nullable = false)
    private String contentKey;

    @Column(name = "pinned_content_version")
    private Integer pinnedContentVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationPriority priority;

    @Column(name = "safety_signal_keys", nullable = false)
    private String safetySignalKeys;

    @Column(name = "start_local_date", nullable = false)
    private LocalDate startLocalDate;

    @Column(name = "local_time", nullable = false)
    private LocalTime localTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "timezone_mode", nullable = false)
    private NotificationTimezoneMode timezoneMode;

    @Column(name = "pinned_timezone")
    private String pinnedTimezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationRecurrence recurrence;

    @Column(name = "ambiguous_policy", nullable = false)
    private String ambiguousPolicy;

    @Column(name = "nonexistent_policy", nullable = false)
    private String nonexistentPolicy;

    @Column(name = "retry_max_attempts", nullable = false)
    private int retryMaxAttempts;

    @Column(name = "retry_initial_backoff_seconds", nullable = false)
    private long retryInitialBackoffSeconds;

    @Column(name = "retry_policy_version", nullable = false)
    private String retryPolicyVersion;

    @Column(name = "next_delivery_at")
    private Instant nextDeliveryAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationScheduleState state;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected NotificationScheduleEntity() {}

    public UUID getScheduleId() { return scheduleId; }
    public String getUserId() { return userId; }
    public NotificationChannel getChannel() { return channel; }
    public String getCategory() { return category; }
    public String getPurpose() { return purpose; }
    public NotificationPriority getPriority() { return priority; }
    public NotificationRecurrence getRecurrence() { return recurrence; }
    public String getPinnedTimezone() { return pinnedTimezone; }
    public NotificationTimezoneMode getTimezoneMode() { return timezoneMode; }
    public LocalDate getStartLocalDate() { return startLocalDate; }
    public LocalTime getLocalTime() { return localTime; }
    public Instant getNextDeliveryAt() { return nextDeliveryAt; }
    public NotificationScheduleState getState() { return state; }
    public long getRowVersion() { return rowVersion; }
}
