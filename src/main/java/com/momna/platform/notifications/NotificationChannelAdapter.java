package com.momna.platform.notifications;

public interface NotificationChannelAdapter {
    NotificationChannel channel();
    boolean available();
}
