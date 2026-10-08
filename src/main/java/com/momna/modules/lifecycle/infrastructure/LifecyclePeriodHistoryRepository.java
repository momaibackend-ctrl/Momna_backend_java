package com.momna.modules.lifecycle.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LifecyclePeriodHistoryRepository extends JpaRepository<LifecyclePeriodHistoryEntity, String> {
    @Query("""
        select e from LifecyclePeriodHistoryEntity e
        where e.userId = :userId
          and e.effectiveFrom <= :at
          and (e.effectiveTo is null or e.effectiveTo > :at)
        order by e.effectiveFrom desc
        """)
    List<LifecyclePeriodHistoryEntity> findCurrent(@Param("userId") String userId, @Param("at") Instant at);

    List<LifecyclePeriodHistoryEntity> findByUserIdOrderByEffectiveFromAsc(String userId);
}
