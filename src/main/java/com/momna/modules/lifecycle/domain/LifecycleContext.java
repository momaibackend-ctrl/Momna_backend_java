package com.momna.modules.lifecycle.domain;

import java.time.Instant;

public record LifecycleContext(
    String id,
    String userId,
    String contextType,
    Instant validFrom,
    Instant validTo,
    String source,
    double confidence
) {}
