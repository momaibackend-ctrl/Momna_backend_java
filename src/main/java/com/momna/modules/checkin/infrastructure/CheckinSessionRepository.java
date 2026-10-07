package com.momna.modules.checkin.infrastructure;

import com.momna.modules.checkin.domain.CheckinPhase;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinSessionRepository extends JpaRepository<CheckinSessionEntity, String> {
    Optional<CheckinSessionEntity> findBySessionIdAndUserId(String sessionId, String userId);

    Optional<CheckinSessionEntity> findByUserIdAndPhaseAndLocalDateAndPeriodAtTime(
        String userId,
        CheckinPhase phase,
        LocalDate localDate,
        String periodAtTime
    );
}
