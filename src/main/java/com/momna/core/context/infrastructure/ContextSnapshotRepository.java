package com.momna.core.context.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContextSnapshotRepository extends JpaRepository<ContextSnapshotEntity, UUID> {
    Optional<ContextSnapshotEntity> findByUserIdAndPurposeKeyAndPurposeVersionAndOperationId(
        String userId,
        String purposeKey,
        int purposeVersion,
        String operationId
    );
}
