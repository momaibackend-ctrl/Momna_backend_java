package com.momna.core.privacy.datalifecycle.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataLifecycleOperationRepository extends JpaRepository<DataLifecycleOperationEntity, UUID> {
    Optional<DataLifecycleOperationEntity> findByIdempotencyKey(String idempotencyKey);
}
