package com.momna.modules.flow.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowDefinitionRepository extends JpaRepository<FlowDefinitionEntity, FlowDefinitionId> {
    List<FlowDefinitionEntity> findByDefinitionKeyOrderByDefinitionVersionDesc(String definitionKey);
}
