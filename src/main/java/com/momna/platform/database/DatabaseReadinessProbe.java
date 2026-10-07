package com.momna.platform.database;

import com.momna.platform.health.ReadinessRegistry.ReadinessProbe;
import java.sql.SQLException;
import javax.sql.DataSource;

public class DatabaseReadinessProbe implements ReadinessProbe {
    private final DataSource dataSource;
    private final int timeoutSeconds;

    public DatabaseReadinessProbe(DataSource dataSource) {
        this(dataSource, 2);
    }

    public DatabaseReadinessProbe(
        DataSource dataSource,
        int timeoutSeconds
    ) {
        this.dataSource = dataSource;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public boolean isReady() {
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(timeoutSeconds);
        } catch (SQLException failure) {
            return false;
        }
    }
}
