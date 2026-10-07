package com.momna.platform.notifications.infrastructure;

import com.momna.platform.notifications.NotificationScheduleState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationScheduleRepository extends JpaRepository<NotificationScheduleEntity, UUID> {
    List<NotificationScheduleEntity> findTop500ByStateAndNextDeliveryAtLessThanEqualOrderByNextDeliveryAtAsc(
        NotificationScheduleState state,
        Instant dueAt
    );
    List<NotificationScheduleEntity> findByUserIdOrderByStartLocalDateAscLocalTimeAsc(String userId);
}
