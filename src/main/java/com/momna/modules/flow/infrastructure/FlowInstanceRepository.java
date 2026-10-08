package com.momna.modules.flow.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowInstanceRepository extends JpaRepository<FlowInstanceEntity, UUID> {
    Optional<FlowInstanceEntity> findByUserIdAndDefinitionKeyAndStartOperationId(
        String userId, String definitionKey, String startOperationId
    );
}
