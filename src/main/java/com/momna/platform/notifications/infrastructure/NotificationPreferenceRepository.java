package com.momna.platform.notifications.infrastructure;

import com.momna.platform.notifications.NotificationChannel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreferenceEntity, NotificationPreferenceId> {

    Optional<NotificationPreferenceEntity> findByUserIdAndChannelAndCategoryAndPurpose(
        String userId,
        NotificationChannel channel,
        String category,
        String purpose
    );
}
