package com.momna.core.fields.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanonicalFieldDefinitionRepository
    extends JpaRepository<CanonicalFieldDefinitionEntity, CanonicalFieldDefinitionId> {

    List<CanonicalFieldDefinitionEntity> findByFieldIdOrderByDefinitionVersionDesc(String fieldId);

    List<CanonicalFieldDefinitionEntity> findByNamespaceOrderByFieldIdAscDefinitionVersionDesc(String namespace);
}
