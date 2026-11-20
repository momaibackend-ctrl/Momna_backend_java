package com.momna.platform.safety;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** A registered safety route: what a decision on this route does and allows. */
public record SafetyRoute(
    String code,
    SafetyDisposition disposition,
    SafetySeverity severity,
    Set<String> allowedActions
) {
    public SafetyRoute {
        Objects.requireNonNull(code);
        Objects.requireNonNull(disposition);
        Objects.requireNonNull(severity);
        allowedActions = Collections.unmodifiableSet(new LinkedHashSet<>(allowedActions));
    }
}
