package com.momna.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiquibaseMigrationInventoryTest {
    @Test
    void allThirtyCanonicalMigrationsArePresent()
        throws Exception {
        var dir = Path.of(
            "src/main/resources/db/liquibase/sql"
        );
        try (var paths = Files.list(dir)) {
            var names = paths
                .filter(path -> path.getFileName()
                    .toString().endsWith(".sql"))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());

            assertEquals(30, names.size());
            for (int version = 1; version <= 30; version++) {
                var prefix = "V" + version + "__";
                var count = names.stream()
                    .filter(name -> name.startsWith(prefix))
                    .count();
                assertEquals(
                    1,
                    count,
                    "Expected exactly one migration for "
                        + prefix
                );
            }
        }
    }
}
