package com.momna.platform.database;

import java.util.Map;

public record DatabaseSettings(
    String databaseUrl,
    int maximumPoolSize
) {
    public DatabaseSettings {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalArgumentException(
                "Database URL must be configured"
            );
        }
        if (maximumPoolSize < 1 || maximumPoolSize > 100) {
            throw new IllegalArgumentException(
                "Database pool size must be between 1 and 100"
            );
        }
    }

    public static DatabaseSettings from(Map<String, String> values) {
        var url = values.get("DATABASE_URL");
        if (url == null || url.isBlank()) {
            throw new DatabaseConfigurationException(
                "Missing required configuration key DATABASE_URL"
            );
        }

        var rawPoolSize = values.get("DATABASE_MAX_POOL_SIZE");
        int poolSize = 10;
        if (rawPoolSize != null && !rawPoolSize.isBlank()) {
            try {
                poolSize = Integer.parseInt(rawPoolSize);
            } catch (NumberFormatException ignored) {
                poolSize = 10;
            }
        }

        return new DatabaseSettings(url, poolSize);
    }

    public static DatabaseSettings fromEnvironment() {
        return from(System.getenv());
    }

    public static final class DatabaseConfigurationException
        extends IllegalStateException {
        public DatabaseConfigurationException(String message) {
            super(message);
        }
    }
}
