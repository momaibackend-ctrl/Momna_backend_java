package com.momna.platform.health;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReadinessRegistry implements ReadinessContract {
    private final List<ReadinessProbe> probes;
    private final List<String> names;
    private final long timeoutMs;

    public ReadinessRegistry() {
        this(List.of(), List.of(), DEFAULT_READINESS_TIMEOUT_MS);
    }

    public ReadinessRegistry(
        List<ReadinessProbe> probes,
        List<String> names,
        long timeoutMs
    ) {
        this.probes = List.copyOf(probes);
        this.names = List.copyOf(names);
        this.timeoutMs = timeoutMs;

        if (this.names.size() != this.probes.size()) {
            throw new IllegalArgumentException(
                "Every readiness probe must have exactly one safe name"
            );
        }
        if (this.names.stream().anyMatch(name ->
            name == null || !name.matches("[a-z0-9._-]+")
        )) {
            throw new IllegalArgumentException(
                "Readiness names must be safe identifiers"
            );
        }
        if (timeoutMs < 1 || timeoutMs > 30_000) {
            throw new IllegalArgumentException(
                "Readiness timeout must be bounded"
            );
        }
    }

    @Override
    public ReadinessReport report() {
        var checks = new ArrayList<ReadinessCheckResult>(probes.size());

        for (int i = 0; i < probes.size(); i++) {
            var started = System.nanoTime();
            boolean healthy;
            try {
                healthy = probes.get(i).isReady();
            } catch (RuntimeException failure) {
                healthy = false;
            }
            var elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            var status = healthy && elapsedMs <= timeoutMs
                ? DependencyStatus.UP
                : DependencyStatus.DOWN;
            checks.add(new ReadinessCheckResult(names.get(i), status, timeoutMs));
        }

        var ready = checks.stream().allMatch(
            check -> check.status() == DependencyStatus.UP
        );
        return new ReadinessReport(ready, checks);
    }

    public boolean isReady() {
        return report().ready();
    }

    public static ReadinessRegistry noExternalDependenciesRegistry() {
        return new ReadinessRegistry();
    }

    public static ReadinessRegistry named(
        Map<String, ReadinessProbe> checks,
        long timeoutMs
    ) {
        var ordered = new LinkedHashMap<>(checks);
        return new ReadinessRegistry(
            List.copyOf(ordered.values()),
            List.copyOf(ordered.keySet()),
            timeoutMs
        );
    }

    @FunctionalInterface
    public interface ReadinessProbe {
        boolean isReady();
    }
}
