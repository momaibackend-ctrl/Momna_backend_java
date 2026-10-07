package com.momna.modules.checkin.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinAnswerRepository extends JpaRepository<CheckinAnswerEntity, CheckinAnswerId> {
    List<CheckinAnswerEntity> findBySessionIdOrderByItemCodeAsc(String sessionId);
    long countBySessionId(String sessionId);
}
