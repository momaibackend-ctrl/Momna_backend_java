package com.momna.platform.health;

import java.util.List;

@FunctionalInterface
public interface ReadinessContract {
    long DEFAULT_READINESS_TIMEOUT_MS = 2_000L;

    ReadinessReport report();

    static ReadinessContract noExternalDependencies() {
        return () -> new ReadinessReport(true, List.of());
    }

    enum DependencyStatus {
        UP, DOWN
    }

    record ReadinessCheckResult(
        String name,
        DependencyStatus status,
        long timeoutMs
    ) {}

    record ReadinessReport(
        boolean ready,
        List<ReadinessCheckResult> checks
    ) {
        public ReadinessReport {
            checks = List.copyOf(checks);
        }
    }
}
