package com.momna.modules.auth.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRefreshHistoryRepository extends JpaRepository<AuthRefreshHistoryEntity, String> {}
