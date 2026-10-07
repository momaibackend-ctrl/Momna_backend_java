package com.momna.platform.health;

import com.momna.platform.database.DatabaseReadinessProbe;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ReadinessHealthIndicator implements HealthIndicator {
    private final ReadinessRegistry readiness;

    public ReadinessHealthIndicator(DataSource dataSource) {
        this.readiness = ReadinessRegistry.named(
            Map.of("database", new DatabaseReadinessProbe(dataSource)),
            ReadinessContract.DEFAULT_READINESS_TIMEOUT_MS
        );
    }

    @Override
    public Health health() {
        var report = readiness.report();
        var builder = report.ready() ? Health.up() : Health.down();
        for (var check : report.checks()) {
            builder.withDetail(check.name(), check.status().name());
        }
        return builder.build();
    }
}
