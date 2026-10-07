package com.momna.modules.lifecycle.domain;

import java.util.List;

public record LifecycleSnapshot(
    LifecycleEntry primary,
    List<LifecycleContext> contexts
) {}
