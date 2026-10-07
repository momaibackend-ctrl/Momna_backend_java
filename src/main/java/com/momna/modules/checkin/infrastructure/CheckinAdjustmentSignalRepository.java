package com.momna.modules.checkin.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinAdjustmentSignalRepository
    extends JpaRepository<CheckinAdjustmentSignalEntity, String> {

    List<CheckinAdjustmentSignalEntity> findByUserIdAndSessionIdOrderBySignalIdAsc(
        String userId,
        String sessionId
    );

    void deleteBySessionId(String sessionId);
}
