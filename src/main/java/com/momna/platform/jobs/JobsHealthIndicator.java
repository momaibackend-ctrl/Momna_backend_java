package com.momna.platform.jobs;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class JobsHealthIndicator implements HealthIndicator {
    private final DurableJobQueue jobs;

    public JobsHealthIndicator(DurableJobQueue jobs) {
        this.jobs = jobs;
    }

    @Override
    public Health health() {
        return jobs.health()
            ? Health.up().withDetail("store", "postgresql").build()
            : Health.down().withDetail("store", "postgresql").build();
    }
}
