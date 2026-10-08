package com.momna.platform.notifications.infrastructure;

import com.momna.platform.notifications.DeliveryAttemptStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryAttemptRepository
    extends JpaRepository<NotificationDeliveryAttemptEntity, UUID> {

    List<NotificationDeliveryAttemptEntity> findByScheduleIdOrderByScheduledAtAscAttemptNumberAsc(UUID scheduleId);

    List<NotificationDeliveryAttemptEntity> findTop500ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
        DeliveryAttemptStatus status,
        Instant dueAt
    );
}
