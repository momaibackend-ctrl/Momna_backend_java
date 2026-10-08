package com.momna.core.privacy.datalifecycle.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataLifecyclePolicyRepository
    extends JpaRepository<DataLifecyclePolicyEntity, DataLifecyclePolicyId> {

    List<DataLifecyclePolicyEntity> findByOwnerAndResourceTypeOrderByPolicyVersionDesc(
        String owner,
        String resourceType
    );
}
