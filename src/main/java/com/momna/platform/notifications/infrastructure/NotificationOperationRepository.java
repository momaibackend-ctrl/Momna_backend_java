package com.momna.platform.notifications.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOperationRepository
    extends JpaRepository<NotificationOperationEntity, String> {}
