package com.momna.platform.notifications.infrastructure;

import com.momna.platform.notifications.NotificationChannel;
import java.io.Serializable;

public class NotificationPreferenceId implements Serializable {
    public String userId;
    public NotificationChannel channel;
    public String category;
    public String purpose;

    public NotificationPreferenceId() {}
}
