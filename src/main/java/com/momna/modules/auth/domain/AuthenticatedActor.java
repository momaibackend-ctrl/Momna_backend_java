package com.momna.modules.auth.domain;

import java.time.Instant;

public record AuthenticatedActor(
    String userId,
    String sessionId,
    Instant authenticatedAt,
    Instant issuedAt
) {}
