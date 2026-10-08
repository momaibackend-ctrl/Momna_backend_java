package com.momna.modules.profile.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecordEntity, String> {
    List<ConsentRecordEntity> findByUserIdOrderByRecordedAtAscIdAsc(String userId);
}
