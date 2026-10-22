package com.momna.e2e;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Seeds disposable-only users and active ER0 R17 states for live HTTP tests.
 * This class is compiled into the dedicated e2e source set, not the app artifact.
 */
public final class SeedE2eDatabase {
    private static final String RULE_VERSION = "medical-safety-rules-v2.2";
    private static final String CATALOG_VERSION = "medical-safety-v2.2";
    private static final Set<String> REQUIRED_CHANGESETS = Set.of(
        "momna-legacy-schema-v1-v30|migration",
        "momna-medical-safety-v31|momna-1145",
        "momna-checkin-hash-column-types-v32|momna-pr29-e2e",
        "momna-checkin-integer-column-types-v33|momna-pr29-e2e",
        "momna-billing-schema-alignment-v34|momna-pr29-e2e"
    );

    private SeedE2eDatabase() {}

    public static void main(String[] args) throws Exception {
        String url = required("DATABASE_URL");
        String username = required("DATABASE_USER");
        String password = required("DATABASE_PASSWORD");

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            verifyLiquibaseHistory(connection);
            connection.setAutoCommit(false);
            try {
                for (var fixture : List.of(
                    new Fixture("systolic-160", "momna-e2e-token-systolic-160",  false, false),
                    new Fixture("diastolic-110", "momna-e2e-token-diastolic-110", false, false),
                    new Fixture("combo-170-115", "momna-e2e-token-combo-170-115", true, false),
                    new Fixture("urgent-140-90", "momna-e2e-token-urgent-140-90", false, false),
                    new Fixture("normal-headache", "momna-e2e-token-normal-headache", true, false),
                    new Fixture("unknown-pressure", "momna-e2e-token-unknown-pressure", false, false)
                )) {
                    seedFixture(connection, fixture);
                }
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
        System.out.println("Seeded 6 isolated authenticated R17 HTTP fixtures.");
    }

    /**
     * Spring Boot owns schema migration at application startup. The fixture seeder
     * must never call Liquibase.update() independently: it verifies the service
     * tables so existing changesets are not replayed by a second migration runner.
     */
    private static void verifyLiquibaseHistory(Connection connection) throws SQLException {
        Set<String> applied = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, author FROM databasechangelog")) {
            while (result.next()) {
                applied.add(result.getString("id") + "|" + result.getString("author"));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(
                "Liquibase DATABASECHANGELOG is missing or inaccessible. Start Spring Boot first " +
                "and let its Liquibase startup finish before seeding E2E fixtures.",
                failure
            );
        }

        Set<String> missing = new HashSet<>(REQUIRED_CHANGESETS);
        missing.removeAll(applied);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Required Liquibase changesets are not recorded in DATABASECHANGELOG: " +
                String.join(", ", missing) +
                ". E2E fixture seeding will not run migrations; fix application startup first."
            );
        }

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT locked FROM databasechangeloglock WHERE id = 1")) {
            if (!result.next()) {
                throw new IllegalStateException(
                    "Liquibase DATABASECHANGELOGLOCK has no row for id=1."
                );
            }
            if (result.getBoolean("locked")) {
                throw new IllegalStateException(
                    "Liquibase DATABASECHANGELOGLOCK is still locked; refusing to seed fixtures."
                );
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(
                "Liquibase DATABASECHANGELOGLOCK is missing or inaccessible.",
                failure
            );
        }
    }

    private static void seedFixture(Connection connection, Fixture fixture) throws Exception {
        String userId = "e2e-bp-" + fixture.id();
        String sessionId = "e2e-session-" + fixture.id();
        String familyId = "e2e-family-" + fixture.id();
        String accessHash = sha256(fixture.token());
        String refreshHash = sha256(fixture.token() + "-refresh");
        String routeStateId = "e2e-route-" + fixture.id();

        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO momna.auth_accounts (user_id, status, created_at, disabled_at)
            VALUES (?, 'ACTIVE', now(), NULL)
            ON CONFLICT (user_id) DO UPDATE SET status = 'ACTIVE', disabled_at = NULL
            """)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO momna.user_profiles (user_id, timezone, account_status, updated_at)
            VALUES (?, 'UTC', 'ACTIVE', now())
            ON CONFLICT (user_id) DO UPDATE
                SET timezone = 'UTC', account_status = 'ACTIVE', updated_at = now()
            """)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO momna.auth_sessions
                (session_id, family_id, user_id, access_hash, refresh_hash,
                 created_at, authenticated_at, access_expires_at, refresh_expires_at,
                 status, device_label, replaced_by_session_id, revoked_at)
            VALUES (?, ?, ?, ?, ?, now(), now(), now() + interval '1 hour',
                    now() + interval '1 day', 'ACTIVE', 'postman-e2e', NULL, NULL)
            ON CONFLICT (session_id) DO UPDATE SET
                access_hash = EXCLUDED.access_hash,
                refresh_hash = EXCLUDED.refresh_hash,
                access_expires_at = EXCLUDED.access_expires_at,
                refresh_expires_at = EXCLUDED.refresh_expires_at,
                status = 'ACTIVE', replaced_by_session_id = NULL, revoked_at = NULL
            """)) {
            statement.setString(1, sessionId);
            statement.setString(2, familyId);
            statement.setString(3, userId);
            statement.setString(4, accessHash);
            statement.setString(5, refreshHash);
            statement.executeUpdate();
        }

        String answers = """
            {"F_BREATHING_SEVERE":"NO","F_THUNDERCLAP_HEADACHE":"NO",
             "F_NEURO_DEFICIT":"NO","F_SEVERE_HEADACHE":"%s",
             "F_VISION_CHANGE":"NO","F_UPPER_ABDO_PAIN":"NO","F_BP_MEASURED":"YES"}
            """.formatted(fixture.headache() ? "YES" : "NO").replaceAll("\\s+", "");

        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO momna.medical_safety_route_states
                (route_state_id, user_id, route_id, session_id, source_item, state,
                 fact_answers, completed_at, cooldown_until, updated_at, reset_observed,
                 disposition, screen_code, action_code, bypass_reason, condition_status,
                 rule_version, catalog_version, revision)
            VALUES (?, ?, 'R17', NULL, 'ER0:POSTPARTUM_PREECLAMPSIA', 'QUESTION',
                    CAST(? AS JSONB), NULL, NULL, now(), FALSE,
                    NULL, NULL, NULL, NULL, NULL, ?, ?, 0)
            ON CONFLICT (user_id, route_id) DO UPDATE SET
                source_item = EXCLUDED.source_item,
                session_id = NULL,
                state = 'QUESTION',
                fact_answers = EXCLUDED.fact_answers,
                completed_at = NULL,
                cooldown_until = NULL,
                updated_at = now(),
                reset_observed = FALSE,
                disposition = NULL,
                screen_code = NULL,
                action_code = NULL,
                bypass_reason = NULL,
                condition_status = NULL,
                rule_version = EXCLUDED.rule_version,
                catalog_version = EXCLUDED.catalog_version,
                revision = 0
            """)) {
            statement.setString(1, routeStateId);
            statement.setString(2, userId);
            statement.setString(3, answers);
            statement.setString(4, RULE_VERSION);
            statement.setString(5, CATALOG_VERSION);
            statement.executeUpdate();
        }
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static String required(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    private record Fixture(String id, String token, boolean headache, boolean unknown) {}
}
