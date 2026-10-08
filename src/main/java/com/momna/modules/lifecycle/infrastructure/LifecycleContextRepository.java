package com.momna.modules.lifecycle.infrastructure;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LifecycleContextRepository extends JpaRepository<LifecycleContextEntity, String> {
    @Query("""
        select c from LifecycleContextEntity c
        where c.userId = :userId
          and c.validFrom <= :at
          and (c.validTo is null or c.validTo > :at)
        order by c.validFrom asc
        """)
    List<LifecycleContextEntity> findActive(@Param("userId") String userId, @Param("at") Instant at);
}
