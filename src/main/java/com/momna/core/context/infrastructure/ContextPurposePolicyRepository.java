package com.momna.core.context.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContextPurposePolicyRepository
    extends JpaRepository<ContextPurposePolicyEntity, ContextPurposePolicyId> {

    List<ContextPurposePolicyEntity> findByPurposeKeyAndActiveTrueOrderByPurposeVersionDesc(String purposeKey);
}
