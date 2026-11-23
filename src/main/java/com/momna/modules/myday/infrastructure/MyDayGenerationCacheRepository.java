package com.momna.modules.myday.infrastructure;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyDayGenerationCacheRepository extends JpaRepository<MyDayGenerationCacheEntity, String> {
    Optional<MyDayGenerationCacheEntity> findFirstByLifeModeAndLanguageAndExpiresAtAfterOrderByCreatedAtDesc(
        String lifeMode, String language, Instant now);
}
