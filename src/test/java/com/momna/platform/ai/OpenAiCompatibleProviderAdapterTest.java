package com.momna.platform.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.platform.ai.AiContracts.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The OpenAI-compatible chat completions call against a local HTTP stub, and its failure mapping. */
class OpenAiCompatibleProviderAdapterTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ModelCandidate CANDIDATE =
        new ModelCandidate("openai-compatible/m#1", "openai-compatible", "test-model", 400, 1600);

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> answer = new AtomicReference<>();
    private final AtomicReference<JsonNode> received = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicInteger delayMillis = new AtomicInteger(0);

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            received.set(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            try { Thread.sleep(delayMillis.get()); } catch (InterruptedException ignored) { }
            var body = (answer.get() == null ? "{}" : answer.get()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsTheSystemAndUserPartsAsChatMessagesInJsonMode() {
        var completion = JSON.createObjectNode();
        completion.putArray("choices").addObject().putObject("message").put("content", "{\"ok\":true}");
        completion.putObject("usage").put("prompt_tokens", 1500).put("completion_tokens", 800);
        answer.set(completion.toString());

        var result = adapter(Duration.ofSeconds(5)).invoke(CANDIDATE, AiPromptSections.join("SYSTEM TEXT", "USER TEXT"),
            null, "trace-1");

        assertEquals("{\"ok\":true}", result.text());
        assertEquals(new TokenUsage(1500, 800), result.usage());
        assertEquals("/v1/chat/completions", path.get());
        assertEquals("Bearer test-key", authorization.get());
        var body = received.get();
        assertEquals("test-model", body.path("model").asText());
        assertEquals("json_object", body.path("response_format").path("type").asText());
        assertEquals("system", body.path("messages").get(0).path("role").asText());
        assertEquals("SYSTEM TEXT", body.path("messages").get(0).path("content").asText());
        assertEquals("user", body.path("messages").get(1).path("role").asText());
        assertEquals("USER TEXT", body.path("messages").get(1).path("content").asText());
        assertEquals(2, body.path("messages").size());
    }

    @Test
    void failuresMapToTheGatewayCategories() {
        assertCategory(429, AiFailureCategory.RATE_LIMIT);
        assertCategory(500, AiFailureCategory.PROVIDER_UNAVAILABLE);
        assertCategory(503, AiFailureCategory.PROVIDER_UNAVAILABLE);
        assertCategory(504, AiFailureCategory.TIMEOUT);
        assertCategory(401, AiFailureCategory.NON_RETRYABLE);
        assertCategory(400, AiFailureCategory.NON_RETRYABLE);

        status.set(200);
        answer.set("not json");
        assertEquals(AiFailureCategory.MALFORMED_OUTPUT, failure(Duration.ofSeconds(5)).category());
        answer.set("{\"choices\":[]}");
        assertEquals(AiFailureCategory.MALFORMED_OUTPUT, failure(Duration.ofSeconds(5)).category());
    }

    @Test
    void aSlowProviderIsATimeout() {
        delayMillis.set(1500);
        answer.set("{}");
        assertEquals(AiFailureCategory.TIMEOUT, failure(Duration.ofMillis(300)).category());
    }

    @Test
    void theKeyIsNeverInAFailureMessage() {
        status.set(401);
        var failure = failure(Duration.ofSeconds(5));
        assertFalse(failure.getMessage().contains("test-key"));
        assertThrows(IllegalArgumentException.class,
            () -> new OpenAiCompatibleProviderAdapter("http://127.0.0.1:1/v1", " ", Duration.ofSeconds(1), 0.7));
        assertThrows(IllegalArgumentException.class,
            () -> new OpenAiCompatibleProviderAdapter("file:///etc", "k", Duration.ofSeconds(1), 0.7));
    }

    private void assertCategory(int code, AiFailureCategory expected) {
        status.set(code);
        answer.set("{\"error\":\"x\"}");
        assertEquals(expected, failure(Duration.ofSeconds(5)).category(), "HTTP " + code);
    }

    private AiProviderException failure(Duration timeout) {
        return assertThrows(AiProviderException.class,
            () -> adapter(timeout).invoke(CANDIDATE, "USER ONLY", null, "trace-1"));
    }

    private OpenAiCompatibleProviderAdapter adapter(Duration timeout) {
        return new OpenAiCompatibleProviderAdapter("http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
            "test-key", timeout, 0.7);
    }
}
