package com.momna.modules.auth.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailChallengeRepository extends JpaRepository<EmailChallengeEntity, String> {}
