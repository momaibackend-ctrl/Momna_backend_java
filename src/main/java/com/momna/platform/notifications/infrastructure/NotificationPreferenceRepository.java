package com.momna.platform.notifications.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreferenceEntity, NotificationPreferenceId> {}
