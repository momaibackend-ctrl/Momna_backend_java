package com.momna.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The public error envelope of every API response: {@code ErrorEnvelope} in openapi.json --
 * {@code code}, {@code message} and {@code requestId} required, {@code fieldErrors} optional, nothing else.
 * The request id is the caller's {@code X-Request-Id} when it sent one, otherwise one generated for the
 * request, exactly as the security entry point does for 401 responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, Map<String, String> fieldErrors, String requestId) {
    private static final String REQUEST_ID_ATTRIBUTE = ApiError.class.getName() + ".requestId";

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of(), currentRequestId());
    }

    public static ApiError of(String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(code, message, Map.copyOf(fieldErrors), currentRequestId());
    }

    static String currentRequestId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return UUID.randomUUID().toString();
        }
        HttpServletRequest request = attributes.getRequest();
        if (request.getAttribute(REQUEST_ID_ATTRIBUTE) instanceof String known) return known;
        var supplied = request.getHeader("X-Request-Id");
        var id = supplied == null || supplied.isBlank() || supplied.length() > 128 ? UUID.randomUUID().toString() : supplied;
        request.setAttribute(REQUEST_ID_ATTRIBUTE, id);
        return id;
    }
}
