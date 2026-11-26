package com.momna.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.platform.ai.AiContracts.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * A provider that speaks the OpenAI-compatible {@code POST /v1/chat/completions} with
 * {@code response_format: json_object}. The API key is read from configuration (an environment
 * variable) and is never logged, cached or put into an exception message. Failures map to the
 * gateway's categories, so retry and fallback stay the gateway's decision.
 */
public class OpenAiCompatibleProviderAdapter implements AiPorts.ProviderAdapter {
    public static final String PROVIDER_KEY = "openai-compatible";

    private final URI endpoint;
    private final String apiKey;
    private final Duration timeout;
    private final double temperature;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public OpenAiCompatibleProviderAdapter(String baseUrl, String apiKey, Duration timeout, double temperature) {
        if (baseUrl == null || !baseUrl.matches("https?://[^\\s]+")) {
            throw new IllegalArgumentException("AI provider base URL is invalid");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("AI provider API key is not configured");
        }
        var base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.endpoint = URI.create(base + "/chat/completions");
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.temperature = temperature;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    @Override
    public ProviderResult invoke(ModelCandidate candidate, String renderedPrompt, ContextEnvelope context, String traceId) {
        var sections = AiPromptSections.split(renderedPrompt);
        var body = json.createObjectNode();
        body.put("model", candidate.modelKey());
        body.put("temperature", temperature);
        body.putObject("response_format").put("type", "json_object");
        var messages = body.putArray("messages");
        if (sections.system() != null) {
            messages.addObject().put("role", "system").put("content", sections.system());
        }
        messages.addObject().put("role", "user").put("content", sections.user());

        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Request-Id", traceId)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException failure) {
            throw new AiProviderException(AiFailureCategory.TIMEOUT, "AI provider timed out");
        } catch (IOException failure) {
            throw new AiProviderException(AiFailureCategory.PROVIDER_UNAVAILABLE, "AI provider is unreachable");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiFailureCategory.PROVIDER_UNAVAILABLE, "AI provider call interrupted");
        }

        int status = response.statusCode();
        if (status == 429) throw new AiProviderException(AiFailureCategory.RATE_LIMIT, "AI provider rate limit");
        if (status == 408 || status == 504) throw new AiProviderException(AiFailureCategory.TIMEOUT, "AI provider timed out");
        if (status >= 500) throw new AiProviderException(AiFailureCategory.PROVIDER_UNAVAILABLE, "AI provider error " + status);
        if (status != 200) throw new AiProviderException(AiFailureCategory.NON_RETRYABLE, "AI provider refused the request " + status);

        JsonNode root;
        try {
            root = json.readTree(response.body());
        } catch (IOException failure) {
            throw new AiProviderException(AiFailureCategory.MALFORMED_OUTPUT, "AI provider answer is not JSON");
        }
        var content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new AiProviderException(AiFailureCategory.MALFORMED_OUTPUT, "AI provider answer has no content");
        }
        var usage = root.path("usage");
        return new ProviderResult(content.asText(), new TokenUsage(
            Math.max(0, usage.path("prompt_tokens").asLong(0)),
            Math.max(0, usage.path("completion_tokens").asLong(0))));
    }
}
