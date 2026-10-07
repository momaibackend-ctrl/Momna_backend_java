package com.momna.modules.auth.domain;

import com.momna.modules.auth.infrastructure.AuthSessionEntity;

public record SessionCredentials(
    String accessCredential,
    String refreshCredential,
    AuthSessionEntity session
) {}
