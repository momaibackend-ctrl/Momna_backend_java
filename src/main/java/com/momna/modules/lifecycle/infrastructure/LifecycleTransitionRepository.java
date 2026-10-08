package com.momna.modules.lifecycle.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LifecycleTransitionRepository
    extends JpaRepository<LifecycleTransitionEntity, String> {
    Optional<LifecycleTransitionEntity> findByIdAndUserId(
        String id,
        String userId
    );
}
