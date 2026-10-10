package com.momna.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.auth.application.EmailChallengeDelivery;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.*;

/**
 * Real HTTP + PostgreSQL E2E. Only the external email delivery is substituted,
 * in this test ApplicationContext; there is no test endpoint or backdoor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AuthenticatedApiHttpE2eTest.DeliveryConfig.class)
class AuthenticatedApiHttpE2eTest {
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
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Value("${local.server.port}")
    int port;

    @TestConfiguration(proxyBeanMethods = false)
    static class DeliveryConfig {
        @Bean
        EmailChallengeDelivery testEmailDelivery() {
            return (email, code) -> DELIVERED_CODE.set(code);
        }
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
        if (bearer != null) req.header("Authorization", "Bearer " + bearer);
        return client.send(req.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (bearer != null) req.header("Authorization", "Bearer " + bearer);
        return client.send(req.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void emailSignInRefreshRotationAndRefreshReuseRevocation() throws Exception {
        String email = "e2e-" + UUID.randomUUID() + "@example.test";
        DELIVERED_CODE.set(null);

        var challenge = post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email", email).toString(), null);
        assertEquals(202, challenge.statusCode(), challenge.body());
        String challengeId = json.readTree(challenge.body()).path("challengeId").asText();
        assertFalse(challengeId.isBlank());
        String code = DELIVERED_CODE.get();
        assertNotNull(code, "Fake delivery in test-only context must receive OTP");

        var completeBody = json.createObjectNode()
            .put("email",email).put("challengeId",challengeId).put("code",code)
            .put("deviceLabel","Automated E2E").toString();
        var login = post("/api/v1/auth/email/complete", completeBody, null);
        assertEquals(200,login.statusCode(),login.body());
        JsonNode loginJson = json.readTree(login.body());
        String access = loginJson.path("accessCredential").asText();
        String refresh = loginJson.path("refreshCredential").asText();
        assertFalse(access.isBlank());
        assertFalse(refresh.isBlank());

        var current = get("/api/v1/me/session",access);
        assertEquals(200,current.statusCode(),current.body());

        var rotation = post("/api/v1/auth/refresh",
            json.createObjectNode().put("refreshCredential",refresh).toString(),null);
        assertEquals(200,rotation.statusCode(),rotation.body());
        String secondAccess = json.readTree(rotation.body()).path("accessCredential").asText();
        assertFalse(secondAccess.isBlank());
        assertNotEquals(access,secondAccess);
        assertEquals(200,get("/api/v1/me/session",secondAccess).statusCode());

        // Kotlin contract: a reused refresh token revokes the entire session family.
        var reuse = post("/api/v1/auth/refresh",
            json.createObjectNode().put("refreshCredential",refresh).toString(),null);
        assertEquals(401,reuse.statusCode(),reuse.body());
        assertEquals(401,get("/api/v1/me/session",secondAccess).statusCode(),
            "Replay must invalidate access in the same family");
    }

    @Test
    void onboardingRouterCanStartResumeAndReplayWithoutDuplicatingInstance() throws Exception {
        String email = "router-" + UUID.randomUUID() + "@example.test";
        DELIVERED_CODE.set(null);
        var challenge = post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email",email).toString(),null);
        assertEquals(202,challenge.statusCode(),challenge.body());
        var challengeId = json.readTree(challenge.body()).path("challengeId").asText();
        var code = DELIVERED_CODE.get();
        assertNotNull(code);
        var login = post("/api/v1/auth/email/complete",
            json.createObjectNode().put("email",email).put("challengeId",challengeId)
                .put("code",code).toString(),null);
        assertEquals(200,login.statusCode(),login.body());
        String access = json.readTree(login.body()).path("accessCredential").asText();
        assertFalse(access.isBlank());

        var key = "router-e2e-" + UUID.randomUUID();
        var body = json.createObjectNode().put("flowType","LIFECYCLE_ROUTER").toString();
        var request = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + "/api/v1/flow-instances"))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer " + access)
            .header("Idempotency-Key",key)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var started = client.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(200,started.statusCode(),started.body());
        var state = json.readTree(started.body());
        var instanceId = state.path("instanceId").asText();
        assertFalse(instanceId.isBlank());
        assertEquals("IN_PROGRESS",state.path("status").asText());

        var resumed = get("/api/v1/flow-instances/"+instanceId,access);
        assertEquals(200,resumed.statusCode(),resumed.body());
        assertEquals(instanceId,json.readTree(resumed.body()).path("instanceId").asText());

        var replay = client.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(200,replay.statusCode(),replay.body());
        var replayed = json.readTree(replay.body());
        assertEquals(instanceId,replayed.path("instanceId").asText());
        assertTrue(replayed.path("idempotentReplay").asBoolean());
    }
}
