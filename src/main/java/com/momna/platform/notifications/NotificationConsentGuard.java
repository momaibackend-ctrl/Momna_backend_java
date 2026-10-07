package com.momna.platform.notifications;

public interface NotificationConsentGuard {
    boolean allowed(String userId, NotificationChannel channel, String category, String purpose);
}
