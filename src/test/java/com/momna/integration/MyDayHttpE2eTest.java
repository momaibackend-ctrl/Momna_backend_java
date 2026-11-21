package com.momna.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.auth.application.EmailChallengeDelivery;
import com.momna.modules.auth.infrastructure.AuthSessionRepository;
import com.momna.modules.lifecycle.domain.LifecyclePeriod;
import com.momna.modules.lifecycle.infrastructure.*;
import com.momna.modules.myday.application.MyDayCardSource;
import com.momna.modules.myday.application.MyDayCards;
import com.momna.modules.myday.application.MyDayException;
import com.momna.modules.profile.infrastructure.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * My Day over real HTTP + PostgreSQL. Substituted in this test context only: email delivery and
 * the card source (fixture text, standing in for the LLM path, which needs an AI provider).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(MyDayHttpE2eTest.TestSources.class)
class MyDayHttpE2eTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry properties) {
        properties.add("DATABASE_URL", POSTGRES::getJdbcUrl);
        properties.add("DATABASE_USER", POSTGRES::getUsername);
        properties.add("DATABASE_PASSWORD", POSTGRES::getPassword);
        properties.add("MOMNA_REDIS_ENABLED", () -> "false");
        properties.add("MOMNA_OBJECT_STORAGE_ENABLED", () -> "false");
    }

    private static final AtomicReference<String> DELIVERED_CODE = new AtomicReference<>();
    private static final AtomicBoolean SOURCE_DOWN = new AtomicBoolean(false);
    private static final List<MyDayCardSource.Input> SOURCE_CALLS = new java.util.concurrent.CopyOnWriteArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSources {
        @Bean
        EmailChallengeDelivery testEmailDelivery() {
            return (email, code) -> DELIVERED_CODE.set(code);
        }

        @Bean
        MyDayCardSource fixtureCardSource() {
            return input -> {
                if (SOURCE_DOWN.get()) {
                    throw new MyDayException("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", "Fixture source is down");
                }
                SOURCE_CALLS.add(input);
                var cards = new ArrayList<MyDayCards.Card>();
                for (var slot : MyDayCards.SLOTS) {
                    for (int i = 1; i <= 3; i++) {
                        cards.add(new MyDayCards.Card(slot, i, "Fixture " + slot + " " + i, "microcopy", "what",
                            "why", "how", "if skipped", "support"));
                    }
                }
                return new MyDayCards.GeneratedDay(cards, "TEST_FIXTURE", "myday.generation.v1",
                    "test.prompt.v1", "test-model", "safety.catalog.v1", "fixture:" + input.lifeMode() + ":" + input.language());
            };
        }
    }

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Value("${local.server.port}") int port;
    @Autowired AuthSessionRepository authSessions;
    @Autowired UserProfileRepository userProfiles;
    @Autowired LifecyclePeriodHistoryRepository lifecycleHistory;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private record User(String token, String userId) {}

    @Test
    void baselineDayIsStoredOnceOwnerOnlyAndReadable() throws Exception {
        var user = user(LifecyclePeriod.CYCLE, "myday-baseline");
        var created = post("/api/v1/my-day/generate", user);
        assertEquals(201, created.statusCode(), created.body());
        var day = json.readTree(created.body());
        assertEquals(9, day.path("cards").size());
        var slots = new ArrayList<String>();
        day.path("cards").forEach(c -> slots.add(c.path("slot").asText() + c.path("position").asInt()));
        assertEquals(List.of("AM1", "AM2", "AM3", "PM1", "PM2", "PM3", "OPTIONAL1", "OPTIONAL2", "OPTIONAL3"), slots);
        assertEquals("am-1", day.path("cards").get(0).path("cardId").asText());
        assertEquals("what", day.path("cards").get(0).path("details").path("whatToDo").asText());
        assertEquals("CYCLE", day.path("lifecyclePeriod").asText());
        assertEquals(1.0, day.path("provenance").path("baselineShare").asDouble());
        assertEquals(0, day.path("provenance").path("sources").size());
        assertEquals(9, day.path("progress").path("pending").asInt());
        assertEquals("adult_cycle", SOURCE_CALLS.getLast().lifeMode());

        var again = post("/api/v1/my-day/generate", user);
        assertEquals(200, again.statusCode(), again.body());
        assertEquals(day.path("myDayId").asText(), json.readTree(again.body()).path("myDayId").asText());
        assertEquals(1, jdbc.queryForObject(
            "SELECT count(*) FROM momna.my_days WHERE user_id = ?", Integer.class, user.userId()));

        assertEquals("READY", day.path("status").asText());
        var today = get("/api/v1/my-day/today", user.token());
        assertEquals(200, today.statusCode(), today.body());
        var todayJson = json.readTree(today.body());
        assertEquals("READY", todayJson.path("state").asText());
        assertEquals(day.path("myDayId").asText(), todayJson.path("myDay").path("myDayId").asText());

        // Idempotency-Key: required; a replay returns the same day; reuse for another request conflicts.
        var noKey = send("POST", "/api/v1/my-day/generate", "{}", user, null);
        assertTrue(noKey.statusCode() >= 400 && noKey.statusCode() < 500, noKey.body());
        String key = "gen-" + UUID.randomUUID();
        var first = send("POST", "/api/v1/my-day/generate", "{\"source\":\"HOME\"}", user, key);
        assertEquals(200, first.statusCode(), first.body());
        var replay = send("POST", "/api/v1/my-day/generate", "{\"source\":\"HOME\"}", user, key);
        assertEquals(200, replay.statusCode(), replay.body());
        assertEquals(day.path("myDayId").asText(), json.readTree(replay.body()).path("myDayId").asText());
        assertEquals("IDEMPOTENCY_CONFLICT", code(send("POST", "/api/v1/my-day/generate",
            "{\"source\":\"RETRY\"}", user, key)));

        var stranger = user(LifecyclePeriod.CYCLE, "myday-stranger");
        assertEquals(404, get("/api/v1/my-day/" + day.path("myDayId").asText(), stranger.token()).statusCode());
        var strangerToday = get("/api/v1/my-day/today", stranger.token());
        assertEquals(200, strangerToday.statusCode(), strangerToday.body());
        assertEquals("NOT_GENERATED", json.readTree(strangerToday.body()).path("state").asText());
        assertTrue(json.readTree(strangerToday.body()).path("myDay").isNull());
        assertEquals(401, get("/api/v1/my-day/" + day.path("myDayId").asText(), null).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayGenerated' AND payload->>'myDayId' = ?", Integer.class, day.path("myDayId").asText()));
        var payload = jdbc.queryForObject("SELECT payload::text FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayGenerated' AND payload->>'myDayId' = ?", String.class, day.path("myDayId").asText());
        assertFalse(payload.contains("Fixture"), "events must not carry card content: " + payload);
    }

    @Test
    void cardProgressIsRevisionedIdempotentAndFinal() throws Exception {
        var user = user(LifecyclePeriod.PREGNANCY, "myday-progress");
        var day = json.readTree(post("/api/v1/my-day/generate", user).body());
        String root = "/api/v1/my-day/" + day.path("myDayId").asText();
        long revision = day.path("revision").asLong();
        String key = "card-" + UUID.randomUUID();
        var done = patch(root + "/cards/am-1", revision, "COMPLETED", user, key);
        assertEquals(200, done.statusCode(), done.body());
        var doneJson = json.readTree(done.body());
        assertEquals("COMPLETED", doneJson.path("cards").get(0).path("status").asText());
        assertEquals(1, doneJson.path("progress").path("completed").asInt());
        assertTrue(doneJson.path("revision").asLong() > revision);

        var replay = patch(root + "/cards/am-1", revision, "COMPLETED", user, key);
        assertEquals(200, replay.statusCode(), replay.body());
        assertEquals(doneJson.path("revision").asLong(), json.readTree(replay.body()).path("revision").asLong());
        assertEquals("IDEMPOTENCY_CONFLICT", code(patch(root + "/cards/am-1", revision, "SKIPPED", user, key)));
        assertEquals("STALE_REVISION", code(patch(root + "/cards/pm-1", revision, "SKIPPED", user, "k-" + UUID.randomUUID())));
        long current = doneJson.path("revision").asLong();
        assertEquals("MY_DAY_CARD_STATE_CONFLICT", code(patch(root + "/cards/am-1", current, "SKIPPED", user, "k-" + UUID.randomUUID())));
        var invalid = patch(root + "/cards/pm-1", current, "PENDING", user, "k-" + UUID.randomUUID());
        assertEquals(422, invalid.statusCode(), invalid.body());
        var unknown = patch(root + "/cards/zz-9", current, "SKIPPED", user, "k-" + UUID.randomUUID());
        assertEquals(404, unknown.statusCode(), unknown.body());

        var skipped = patch(root + "/cards/optional-2", current, "SKIPPED", user, "k-" + UUID.randomUUID());
        assertEquals(200, skipped.statusCode(), skipped.body());
        var progress = json.readTree(skipped.body()).path("progress");
        assertEquals(1, progress.path("completed").asInt());
        assertEquals(1, progress.path("skipped").asInt());
        assertEquals(7, progress.path("pending").asInt());

        // Progress is durable: a later generate returns the same day with its progress.
        var later = json.readTree(post("/api/v1/my-day/generate", user).body());
        assertEquals(day.path("myDayId").asText(), later.path("myDayId").asText());
        assertEquals(2, later.path("progress").path("completed").asInt() + later.path("progress").path("skipped").asInt());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayCardCompleted' AND payload->>'myDayId' = ?", Integer.class, day.path("myDayId").asText()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayCardSkipped' AND payload->>'myDayId' = ?", Integer.class, day.path("myDayId").asText()));

        // MyDayCompleted: once, when every AM and PM card is final; optional cards do not count.
        long revisionNow = json.readTree(skipped.body()).path("revision").asLong();
        for (var cardId : List.of("am-2", "am-3", "pm-1", "pm-2", "pm-3")) {
            String status = "pm-3".equals(cardId) ? "SKIPPED" : "COMPLETED";
            String countBefore = String.valueOf(jdbc.queryForObject("SELECT count(*) FROM momna.transactional_outbox " +
                "WHERE event_type = 'MyDayCompleted' AND payload->>'myDayId' = ?", Integer.class, day.path("myDayId").asText()));
            assertEquals("0", countBefore, "completed before " + cardId);
            var updated = patch(root + "/cards/" + cardId, revisionNow, status, user, "k-" + UUID.randomUUID());
            assertEquals(200, updated.statusCode(), updated.body());
            revisionNow = json.readTree(updated.body()).path("revision").asLong();
        }
        var completion = jdbc.queryForList("SELECT payload::text AS p FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayCompleted' AND payload->>'myDayId' = ?", day.path("myDayId").asText());
        assertEquals(1, completion.size());
        var completionPayload = json.readTree((String) completion.get(0).get("p"));
        assertEquals(5, completionPayload.path("completedCount").asInt());
        assertEquals(1, completionPayload.path("skippedCount").asInt());
        assertFalse(completion.get(0).get("p").toString().contains("Fixture"));
        var optional = patch(root + "/cards/optional-1", revisionNow, "COMPLETED", user, "k-" + UUID.randomUUID());
        assertEquals(200, optional.statusCode(), optional.body());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayCompleted' AND payload->>'myDayId' = ?", Integer.class, day.path("myDayId").asText()));

        var stranger = user(LifecyclePeriod.PREGNANCY, "myday-progress-stranger");
        var foreign = patch(root + "/cards/pm-1", current, "SKIPPED", stranger, "k-" + UUID.randomUUID());
        assertEquals(404, foreign.statusCode(), foreign.body());
    }

    @Test
    void submittedMorningCheckinFeedsTheDayAndIsConsumedOnlyAfterItIsStored() throws Exception {
        // PLANNING: no other test generates it, so there is no verified day to fall back to.
        var user = user(LifecyclePeriod.PLANNING, "myday-morning");
        var session = json.readTree(postWithKey("/api/v1/check-in/sessions",
            json.createObjectNode().put("phase", "MORNING").toString(), user, "s-" + UUID.randomUUID()).body());
        String endpoint = "/api/v1/check-in/sessions/" + session.path("sessionId").asText();
        var change = json.createObjectNode().put("expectedRevision", session.path("revision").asLong());
        change.putArray("answerChanges").addObject()
            .put("itemCode", session.path("items").get(0).path("itemCode").asText()).put("value", 1);
        var patched = json.readTree(send("PATCH", endpoint, change.toString(), user, "p-" + UUID.randomUUID()).body());
        var submitted = postWithKey(endpoint + "/submit", json.createObjectNode()
            .put("expectedRevision", patched.path("revision").asLong()).toString(), user, "sub-" + UUID.randomUUID());
        assertEquals(200, submitted.statusCode(), submitted.body());

        // The source is down: nothing is stored and the Check-in is not consumed.
        SOURCE_DOWN.set(true);
        try {
            var failed = post("/api/v1/my-day/generate", user);
            assertEquals(503, failed.statusCode(), failed.body());
            assertEquals("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", json.readTree(failed.body()).path("code").asText());
            assertEquals("30", failed.headers().firstValue("Retry-After").orElse(null));
        } finally {
            SOURCE_DOWN.set(false);
        }
        assertNull(jdbc.queryForObject("SELECT consumed_by_ref FROM momna.checkin_sessions WHERE session_id = ?",
            String.class, session.path("sessionId").asText()));
        assertEquals(0, jdbc.queryForObject(
            "SELECT count(*) FROM momna.my_days WHERE user_id = ?", Integer.class, user.userId()));

        var created = post("/api/v1/my-day/generate", user);
        assertEquals(201, created.statusCode(), created.body());
        var day = json.readTree(created.body());
        var sources = day.path("provenance").path("sources");
        assertEquals(1, sources.size(), day.path("provenance").toString());
        assertEquals("MORNING", sources.get(0).path("phase").asText());
        assertEquals(day.path("localDate").asText(), sources.get(0).path("sourceLocalDate").asText());
        assertEquals("SUBMITTED", sources.get(0).path("state").asText());
        assertTrue(day.path("provenance").path("baselineShare").asDouble() < 1.0);
        assertFalse(sources.get(0).has("sourceRef"), "session ids are not part of the public provenance");
        assertEquals(day.path("myDayId").asText(), jdbc.queryForObject(
            "SELECT consumed_by_ref FROM momna.checkin_sessions WHERE session_id = ?",
            String.class, session.path("sessionId").asText()));
    }

    @Test
    void whenGenerationIsDownASimilarVerifiedDayIsServedAsDegraded() throws Exception {
        var first = user(LifecyclePeriod.MENOPAUSE, "myday-cache-source");
        assertEquals(201, post("/api/v1/my-day/generate", first).statusCode());
        SOURCE_DOWN.set(true);
        try {
            var second = user(LifecyclePeriod.MENOPAUSE, "myday-cache-fallback");
            var served = post("/api/v1/my-day/generate", second);
            assertEquals(201, served.statusCode(), served.body());
            var day = json.readTree(served.body());
            assertEquals("DEGRADED_READY", day.path("status").asText());
            assertEquals("CACHE_FALLBACK", day.path("provenance").path("cardSource").asText());
            assertEquals(9, day.path("cards").size());
            assertEquals("DEGRADED_READY", json.readTree(get("/api/v1/my-day/today", second.token()).body())
                .path("state").asText());
            // The cache is a template, not the other user's day: separate ids, separate progress.
            assertNotEquals(json.readTree(get("/api/v1/my-day/today", first.token()).body())
                .path("myDay").path("myDayId").asText(), day.path("myDayId").asText());
        } finally {
            SOURCE_DOWN.set(false);
        }
    }

    private User user(LifecyclePeriod period, String label) throws Exception {
        String email = label + "-" + UUID.randomUUID() + "@example.test";
        DELIVERED_CODE.set(null);
        var challenge = send("POST", "/api/v1/auth/email/challenges",
            json.createObjectNode().put("email", email).toString(), null, null);
        assertEquals(202, challenge.statusCode(), challenge.body());
        var login = send("POST", "/api/v1/auth/email/complete", json.createObjectNode()
            .put("email", email)
            .put("challengeId", json.readTree(challenge.body()).path("challengeId").asText())
            .put("code", DELIVERED_CODE.get()).toString(), null, null);
        assertEquals(200, login.statusCode(), login.body());
        var credentials = json.readTree(login.body());
        String userId = authSessions.findById(credentials.path("session").path("sessionId").asText())
            .orElseThrow().getUserId();
        var profile = new UserProfileEntity(userId, Instant.now());
        String tz = LocalTime.now(ZoneOffset.ofHours(2)).isBefore(LocalTime.NOON) ? "Etc/GMT-2" : "Etc/GMT+10";
        profile.updateLocalization("en", "en-US", "US", tz, Instant.now());
        userProfiles.saveAndFlush(profile);
        lifecycleHistory.saveAndFlush(new LifecyclePeriodHistoryEntity(UUID.randomUUID().toString(), userId, period,
            null, Instant.now().minusSeconds(86400), null, label, 1.0, true));
        return new User(credentials.path("accessCredential").asText(), userId);
    }

    private String code(HttpResponse<String> response) throws Exception {
        assertTrue(response.statusCode() >= 400, response.body());
        return json.readTree(response.body()).path("code").asText();
    }

    private HttpResponse<String> post(String path, User user) throws Exception {
        return send("POST", path, "{}", user, "gen-" + UUID.randomUUID());
    }

    private HttpResponse<String> postWithKey(String path, String body, User user, String key) throws Exception {
        return send("POST", path, body, user, key);
    }

    private HttpResponse<String> patch(String path, long revision, String status, User user, String key) throws Exception {
        return send("PATCH", path, json.createObjectNode().put("expectedRevision", revision)
            .put("status", status).toString(), user, key);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String body, User user, String key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
        if (user != null) request.header("Authorization", "Bearer " + user.token());
        if (key != null) request.header("Idempotency-Key", key);
        return client.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

}
