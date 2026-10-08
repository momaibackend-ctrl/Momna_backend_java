package com.momna.modules.content.infrastructure;

import com.momna.modules.content.ContentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentVersionRepository extends JpaRepository<ContentVersionEntity, ContentVersionId> {
    List<ContentVersionEntity> findByContentIdOrderByVersionDesc(String contentId);

    Optional<ContentVersionEntity> findFirstByContentIdAndStatusAndPublishedAtLessThanEqualOrderByVersionDesc(
        String contentId,
        ContentStatus status,
        Instant effectiveAt
    );
}
