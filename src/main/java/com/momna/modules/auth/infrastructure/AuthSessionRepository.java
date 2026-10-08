package com.momna.modules.auth.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {
    Optional<AuthSessionEntity> findByAccessHash(String accessHash);
    Optional<AuthSessionEntity> findByRefreshHash(String refreshHash);
    List<AuthSessionEntity> findByUserIdOrderByCreatedAtDescSessionIdAsc(String userId);
    List<AuthSessionEntity> findByFamilyId(String familyId);
}
