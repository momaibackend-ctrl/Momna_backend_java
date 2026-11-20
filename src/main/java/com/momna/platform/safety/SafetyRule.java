package com.momna.platform.safety;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** A deterministic rule: any of its signal keys selects its route. */
public record SafetyRule(String id, String version, Set<String> signalKeys, String routeCode) {
    public SafetyRule {
        Objects.requireNonNull(id);
        Objects.requireNonNull(version);
        Objects.requireNonNull(routeCode);
        signalKeys = Collections.unmodifiableSet(new LinkedHashSet<>(signalKeys));
    }
}
