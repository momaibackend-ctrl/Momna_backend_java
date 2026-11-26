package com.momna.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.auth.application.EmailChallengeDelivery;
import com.momna.modules.auth.infrastructure.AuthSessionRepository;
import com.momna.modules.lifecycle.domain.LifecyclePeriod;
import com.momna.modules.lifecycle.infrastructure.*;
import com.momna.modules.profile.infrastructure.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
 * MOMNA-849 end to end: POST /my-day/generate -> LlmMyDayCardSource -> SafetyGuardedAi -> canonical gateway
 * -> OpenAI-compatible HTTP call to a local model stub -> validation -> my_days and the anonymous template
 * cache in PostgreSQL. The stub is the only fake; everything between the API and the HTTP call is real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(MyDayGenerationHttpE2eTest.TestSources.class)
class MyDayGenerationHttpE2eTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API_KEY = "e2e-test-key-not-a-secret";
    /** What the stub answers next: VALID, EIGHT_CARDS or HTTP_500. Empty queue = VALID. */
    private static final ConcurrentLinkedQueue<String> SCRIPT = new ConcurrentLinkedQueue<>();
    private static final List<JsonNode> CALLS = new CopyOnWriteArrayList<>();
    private static final List<String> AUTHORIZATIONS = new CopyOnWriteArrayList<>();
    private static final HttpServer MODEL = startModel();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        properties.add("DATABASE_URL", POSTGRES::getJdbcUrl);
        properties.add("DATABASE_USER", POSTGRES::getUsername);
        properties.add("DATABASE_PASSWORD", POSTGRES::getPassword);
        properties.add("MOMNA_REDIS_ENABLED", () -> "false");
        properties.add("MOMNA_OBJECT_STORAGE_ENABLED", () -> "false");
        properties.add("MOMNA_AI_MYDAY_ENABLED", () -> "true");
        properties.add("MOMNA_AI_BASE_URL", () -> "http://127.0.0.1:" + MODEL.getAddress().getPort() + "/v1");
        properties.add("MOMNA_AI_API_KEY", () -> API_KEY);
        properties.add("MOMNA_AI_MYDAY_MODEL", () -> "stub-model");
    }

    private static final AtomicReference<String> DELIVERED_CODE = new AtomicReference<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSources {
        @Bean
        EmailChallengeDelivery testEmailDelivery() {
            return (email, code) -> DELIVERED_CODE.set(code);
        }
    }

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${local.server.port}") int port;
    @Autowired AuthSessionRepository authSessions;
    @Autowired UserProfileRepository userProfiles;
    @Autowired LifecyclePeriodHistoryRepository lifecycleHistory;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private record User(String token, String userId, String email) {}

    @BeforeEach
    void reset() {
        SCRIPT.clear();
    }

    @AfterAll
    static void stopModel() {
        MODEL.stop(0);
    }

    @Test
    void theModelWritesTheDayAndTheSameAnonymousKeyIsServedFromTheTemplateCache() throws Exception {
        int before = CALLS.size();
        var first = user(LifecyclePeriod.CYCLE, "gen-first");
        var created = generate(first);
        assertEquals(201, created.statusCode(), created.body());
        var day = JSON.readTree(created.body());
        assertEquals(9, day.path("cards").size());
        assertEquals("Model morning 1", day.path("cards").get(0).path("title").asText());
        assertEquals("Model morning 1 what_to_do", day.path("cards").get(0).path("details").path("whatToDo").asText());
        assertEquals(before + 1, CALLS.size());
        assertEquals("Bearer " + API_KEY, AUTHORIZATIONS.getLast());

        var call = CALLS.getLast();
        assertEquals("stub-model", call.path("model").asText());
        assertEquals("json_object", call.path("response_format").path("type").asText());
        assertEquals("system", call.path("messages").get(0).path("role").asText());
        var userPrompt = call.path("messages").get(1).path("content").asText();
        assertTrue(userPrompt.contains("life_mode: adult_cycle\n"), userPrompt);
        assertTrue(userPrompt.contains("user_language: en\n"), userPrompt);
        assertFalse(call.toString().contains("{{"), "every prompt variable is rendered");
        assertFalse(call.toString().contains(first.userId()), "the user id never reaches the model");
        assertFalse(call.toString().contains(first.email()), "the email never reaches the model");

        var stored = row(first);
        assertEquals("LLM", stored.cardSource());
        assertEquals("myday.prompts.v1.2", stored.promptVersion());
        assertEquals("openai-compatible/stub-model#1", stored.modelRef());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM momna.my_day_template_cache " +
            "WHERE template_fingerprint = ?", Integer.class, stored.cacheKey()));
        var templateColumns = jdbc.queryForObject("SELECT row_to_json(t)::text FROM momna.my_day_template_cache t " +
            "WHERE template_fingerprint = ?", String.class, stored.cacheKey());
        assertFalse(templateColumns.contains(first.userId()), "the template is anonymous");
        var ledger = jdbc.queryForList("SELECT subject_hash, purpose, cost_micros FROM momna.ai_cost_ledger " +
            "WHERE request_id LIKE 'myday:%'");
        assertFalse(ledger.isEmpty());
        assertTrue(ledger.stream().noneMatch(r -> first.userId().equals(r.get("subject_hash"))));

        var second = user(LifecyclePeriod.CYCLE, "gen-second");
        var again = generate(second);
        assertEquals(201, again.statusCode(), again.body());
        var secondRow = row(second);
        assertEquals(stored.cacheKey(), secondRow.cacheKey(), "same anonymous inputs, same template key");
        assertEquals("TEMPLATE_CACHE", secondRow.cardSource());
        assertEquals(before + 1, CALLS.size(), "a template hit does not call the model");
        assertEquals(titles(day), titles(JSON.readTree(again.body())));
    }

    @Test
    void anInvalidAnswerIsRetriedOnceAndTwoInvalidAnswersStoreNothing() throws Exception {
        int before = CALLS.size();
        SCRIPT.add("EIGHT_CARDS");
        SCRIPT.add("EIGHT_CARDS");
        var user = user(LifecyclePeriod.MENOPAUSE, "gen-invalid");
        var failed = generate(user);
        assertEquals(503, failed.statusCode(), failed.body());
        assertEquals("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", JSON.readTree(failed.body()).path("code").asText());
        assertEquals(before + 2, CALLS.size(), "one retry");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM momna.my_days WHERE user_id = ?", Integer.class,
            user.userId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM momna.my_day_template_cache WHERE life_mode = 'menopause'",
            Integer.class), "an invalid answer is never cached");

        SCRIPT.add("EIGHT_CARDS");
        var retried = generate(user);
        assertEquals(201, retried.statusCode(), retried.body());
        assertEquals(before + 4, CALLS.size());
        assertEquals("openai-compatible/stub-model#2", row(user).modelRef());
    }

    @Test
    void aProviderErrorIsRetriedOnce() throws Exception {
        int before = CALLS.size();
        SCRIPT.add("HTTP_500");
        var user = user(LifecyclePeriod.PLANNING, "gen-provider-error");
        var created = generate(user);
        assertEquals(201, created.statusCode(), created.body());
        assertEquals(before + 2, CALLS.size());
        assertEquals("LLM", row(user).cardSource());
    }

    private record Row(String cardSource, String promptVersion, String modelRef, String cacheKey) {}

    private Row row(User user) {
        return jdbc.queryForObject("SELECT card_source, prompt_version, model_ref, cache_key FROM momna.my_days " +
            "WHERE user_id = ?", (rs, n) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
            user.userId());
    }

    private static List<String> titles(JsonNode day) {
        var out = new ArrayList<String>();
        day.path("cards").forEach(c -> out.add(c.path("title").asText()));
        return out;
    }

    private static HttpServer startModel() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                AUTHORIZATIONS.add(exchange.getRequestHeaders().getFirst("Authorization"));
                CALLS.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
                var mode = SCRIPT.poll();
                int status = "HTTP_500".equals(mode) ? 500 : 200;
                var cards = JSON.createObjectNode();
                for (var group : List.of("morning", "evening", "optional")) {
                    var items = cards.putArray(group);
                    int count = "EIGHT_CARDS".equals(mode) && group.equals("optional") ? 2 : 3;
                    for (int i = 1; i <= count; i++) {
                        var card = items.addObject();
                        card.put("title", "Model " + group + " " + i);
                        for (var field : List.of("microcopy", "what_to_do", "why_today", "how_to_do", "if_skipped", "support")) {
                            card.put(field, "Model " + group + " " + i + " " + field);
                        }
                    }
                }
                var completion = JSON.createObjectNode();
                completion.putArray("choices").addObject().putObject("message").put("content", cards.toString());
                completion.putObject("usage").put("prompt_tokens", 2500).put("completion_tokens", 1800);
                var body = (status == 200 ? completion.toString() : "{\"error\":\"down\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private HttpResponse<String> generate(User user) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/my-day/generate"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + user.token())
            .header("Idempotency-Key", "gen-" + UUID.randomUUID())
            .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** A user in the morning window, as in MyDayHttpE2eTest. */
    private User user(LifecyclePeriod period, String label) throws Exception {
        String email = label + "-" + UUID.randomUUID() + "@example.test";
        DELIVERED_CODE.set(null);
        var challenge = post("/api/v1/auth/email/challenges", JSON.createObjectNode().put("email", email).toString());
        assertEquals(202, challenge.statusCode(), challenge.body());
        var login = post("/api/v1/auth/email/complete", JSON.createObjectNode().put("email", email)
            .put("challengeId", JSON.readTree(challenge.body()).path("challengeId").asText())
            .put("code", DELIVERED_CODE.get()).toString());
        assertEquals(200, login.statusCode(), login.body());
        var credentials = JSON.readTree(login.body());
        String userId = authSessions.findById(credentials.path("session").path("sessionId").asText())
            .orElseThrow().getUserId();
        var profile = new UserProfileEntity(userId, Instant.now());
        profile.updateLocalization("en", "en-US", "US", zoneAt(2, 9), Instant.now());
        userProfiles.saveAndFlush(profile);
        lifecycleHistory.saveAndFlush(new LifecyclePeriodHistoryEntity(UUID.randomUUID().toString(), userId, period,
            null, Instant.now().minusSeconds(86400), null, label, 1.0, true));
        return new User(credentials.path("accessCredential").asText(), userId, email);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String zoneAt(int fromHour, int toHour) {
        var now = Instant.now();
        for (int offset = -12; offset <= 14; offset++) {
            int hour = now.atOffset(ZoneOffset.ofHours(offset)).getHour();
            if (hour >= fromHour && hour <= toHour) {
                return offset == 0 ? "Etc/GMT" : offset > 0 ? "Etc/GMT-" + offset : "Etc/GMT+" + (-offset);
            }
        }
        throw new IllegalStateException("no zone with local hour " + fromHour + "-" + toHour);
    }
}
