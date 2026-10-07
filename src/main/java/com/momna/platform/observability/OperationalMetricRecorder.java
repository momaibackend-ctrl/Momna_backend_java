package com.momna.platform.observability;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetricRecorder {
    private final StructuredObservabilityService observability;

    public OperationalMetricRecorder(
        StructuredObservabilityService observability
    ) {
        this.observability = observability;
    }

    public void databasePool(
        int active,
        int pending,
        TelemetryContext context
    ) {
        if (active < 0 || pending < 0) {
            throw new IllegalArgumentException(
                "Database pool metrics must be non-negative"
            );
        }
        observability.recordMetric(
            "db.pool.active",
            active,
            context,
            Map.of("pool", "primary")
        );
        observability.recordMetric(
            "db.pool.pending",
            pending,
            context,
            Map.of("pool", "primary")
        );
    }

    public void jobQueue(
        int depth,
        int dead,
        String jobType,
        TelemetryContext context
    ) {
        if (depth < 0 || dead < 0) {
            throw new IllegalArgumentException(
                "Job queue metrics must be non-negative"
            );
        }
        var labels = Map.of(
            "queue", "durable",
            "job_type", jobType
        );
        observability.recordMetric(
            "job.queue.depth",
            depth,
            context,
            labels
        );
        observability.recordMetric(
            "job.dead.total",
            dead,
            context,
            labels
        );
    }

    public void cache(
        boolean hit,
        TelemetryContext context
    ) {
        observability.recordMetric(
            hit ? "cache.hit.total" : "cache.miss.total",
            1.0,
            context,
            Map.of("cache", "shared")
        );
    }

    public void ai(
        long latencyMillis,
        long costMicros,
        String provider,
        TelemetryContext context,
        TelemetryErrorCode error,
        boolean validationRetry
    ) {
        if (latencyMillis < 0 || costMicros < 0) {
            throw new IllegalArgumentException(
                "AI operational metrics must be non-negative"
            );
        }

        var providerLabel = Map.of("provider", provider);
        observability.recordMetric(
            "ai.latency.ms",
            latencyMillis,
            context,
            providerLabel
        );
        observability.recordMetric(
            "ai.cost.usd_micros",
            costMicros,
            context,
            providerLabel
        );

        if (error != null) {
            observability.recordMetric(
                "ai.error.total",
                1.0,
                context,
                Map.of(
                    "provider", provider,
                    "error_category",
                    error.category().name().toLowerCase()
                )
            );
        }

        if (validationRetry) {
            observability.recordMetric(
                "ai.validation_retry.total",
                1.0,
                context,
                providerLabel
            );
        }
    }

    public void notification(
        boolean delivered,
        String channel,
        TelemetryContext context
    ) {
        observability.recordMetric(
            "notification.delivery.total",
            1.0,
            context,
            Map.of(
                "channel", channel,
                "outcome", delivered ? "delivered" : "failed"
            )
        );
    }
}
