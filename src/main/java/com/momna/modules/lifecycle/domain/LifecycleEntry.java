package com.momna.modules.lifecycle.domain;

import java.time.Instant;

public record LifecycleEntry(
    String id,
    String userId,
    LifecyclePeriod period,
    String substage,
    Instant effectiveFrom,
    Instant effectiveTo,
    String source,
    double confidence,
    boolean selectedManually
) {}
