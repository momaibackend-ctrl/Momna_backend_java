package com.momna.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// The error body is openapi.json's ErrorEnvelope -- code, message, requestId, optional fieldErrors,
// nothing else -- and a validation failure names the first invalid field the way the working Kotlin
// API does, so the two implementations answer the same request identically.
class ErrorEnvelopeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    record CompleteEmail(@NotBlank String challengeId, @NotBlank @Email String email, @NotBlank String code) {}

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void envelopeCarriesTheCallersRequestIdAndNoOtherFields() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        JsonNode json = mapper.readTree(mapper.writeValueAsString(ApiError.of("NOT_FOUND", "Resource not found")));
        assertThat(json.get("requestId").asText()).isEqualTo("req-123");
        assertThat(iterable(json.fieldNames())).containsExactlyInAnyOrder("code", "message", "requestId");
    }

    @Test
    void validationNamesTheFirstMissingFieldInDeclaredOrder() throws Exception {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        var target = new CompleteEmail(null, null, null);
        var result = new BeanPropertyBindingResult(target, "request");
        // Bean validation reports violations in no stable order; the answer must not depend on it.
        result.addError(new FieldError("request", "code", null, false, new String[] {"NotBlank"}, null, "must not be blank"));
        result.addError(new FieldError("request", "email", null, false, new String[] {"NotBlank"}, null, "must not be blank"));
        result.addError(new FieldError("request", "challengeId", null, false, new String[] {"NotBlank"}, null, "must not be blank"));
        var parameter = new MethodParameter(ErrorEnvelopeTest.class.getDeclaredMethod("handlerShape", CompleteEmail.class), 0);
        var response = new GlobalExceptionHandler().validation(new MethodArgumentNotValidException(parameter, result));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(response.getBody()));
        assertThat(json.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json.get("message").asText()).isEqualTo("Missing required field");
        assertThat(json.get("fieldErrors").toString()).isEqualTo("{\"challengeId\":\"required\"}");
        assertThat(json.get("requestId").asText()).isNotBlank();
        assertThat(json.has("timestamp")).isFalse();
    }

    @SuppressWarnings("unused")
    private void handlerShape(CompleteEmail request) {}

    private static Set<String> iterable(java.util.Iterator<String> names) {
        var values = new java.util.HashSet<String>();
        names.forEachRemaining(values::add);
        return values;
    }
}
