package com.momna.core.fields.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanonicalFieldValueRepository extends JpaRepository<CanonicalFieldValueEntity, String> {
    List<CanonicalFieldValueEntity> findByUserIdAndScopeTypeAndScopeIdOrderByRecordedAtDesc(
        String userId, String scopeType, String scopeId
    );

    List<CanonicalFieldValueEntity> findByUserIdAndFieldIdOrderByRecordedAtDesc(
        String userId,
        String fieldId
    );
}
