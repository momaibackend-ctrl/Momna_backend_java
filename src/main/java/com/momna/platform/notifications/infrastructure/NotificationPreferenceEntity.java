package com.momna.platform.notifications.infrastructure;

import com.momna.platform.notifications.NotificationChannel;
import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "notification_preferences", schema = "momna")
@IdClass(NotificationPreferenceId.class)
public class NotificationPreferenceEntity {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    @Id
    private String category;

    @Id
    private String purpose;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "quiet_start")
    private LocalTime quietStart;

    @Column(name = "quiet_end")
    private LocalTime quietEnd;

    @Column(name = "quiet_policy_version")
    private String quietPolicyVersion;

    @Version
    @Column(nullable = false)
    private long version;

    protected NotificationPreferenceEntity() {}

    public NotificationPreferenceEntity(
        String userId,
        NotificationChannel channel,
        String category,
        String purpose,
        boolean enabled,
        LocalTime quietStart,
        LocalTime quietEnd,
        String quietPolicyVersion
    ) {
        this.userId = userId;
        this.channel = channel;
        this.category = category;
        this.purpose = purpose;
        this.enabled = enabled;
        this.quietStart = quietStart;
        this.quietEnd = quietEnd;
        this.quietPolicyVersion = quietPolicyVersion;
    }

    public void update(
        boolean enabled,
        LocalTime quietStart,
        LocalTime quietEnd,
        String quietPolicyVersion
    ) {
        this.enabled = enabled;
        this.quietStart = quietStart;
        this.quietEnd = quietEnd;
        this.quietPolicyVersion = quietPolicyVersion;
    }

    public String getUserId() { return userId; }
    public NotificationChannel getChannel() { return channel; }
    public String getCategory() { return category; }
    public String getPurpose() { return purpose; }
    public boolean isEnabled() { return enabled; }
    public LocalTime getQuietStart() { return quietStart; }
    public LocalTime getQuietEnd() { return quietEnd; }
    public String getQuietPolicyVersion() { return quietPolicyVersion; }
    public long getVersion() { return version; }
}
