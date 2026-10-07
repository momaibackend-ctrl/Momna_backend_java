package com.momna.shared.api;

import com.momna.modules.auth.application.AuthException;
import com.momna.modules.billing.application.BillingRestoreService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", "Request validation failed"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", "Request is invalid"));
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ApiError> auth(AuthException ex) {
        var status = switch (ex.code()) {
            case "AUTH_REQUIRED", "AUTH_INVALID", "AUTH_EXPIRED", "SESSION_REVOKED" -> HttpStatus.UNAUTHORIZED;
            case "REAUTH_REQUIRED", "ACCOUNT_DISABLED" -> HttpStatus.FORBIDDEN;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "CORE_API_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "IDENTITY_ALREADY_LINKED", "IDENTITY_LINK_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNAUTHORIZED;
        };
        return ResponseEntity.status(status).body(ApiError.of(ex.code(), publicMessage(ex.code())));
    }

    @ExceptionHandler(BillingRestoreService.BillingException.class)
    ResponseEntity<ApiError> billing(BillingRestoreService.BillingException ex) {
        var status = switch (ex.code()) {
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "PURCHASE_ALREADY_CLAIMED" -> HttpStatus.CONFLICT;
            case "AUTH_INVALID" -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).body(ApiError.of(ex.code(), switch (status) {
            case TOO_MANY_REQUESTS -> "Billing request rate limit exceeded";
            case CONFLICT -> "Purchase belongs to another account";
            case UNAUTHORIZED -> "Authentication is required or no longer valid";
            default -> "Billing state could not be verified";
        }));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> stateConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("STATE_CONFLICT", "State changed; refresh and retry"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of("INTERNAL_ERROR", "Unexpected server error"));
    }

    private String publicMessage(String code) {
        return switch (code) {
            case "AUTH_REQUIRED", "AUTH_INVALID", "AUTH_EXPIRED", "SESSION_REVOKED" ->
                "Authentication is required or no longer valid";
            case "REAUTH_REQUIRED" -> "Recent authentication is required";
            case "ACCOUNT_DISABLED" -> "Account is unavailable";
            case "RATE_LIMITED" -> "Request rate limit exceeded";
            case "CORE_API_UNAVAILABLE" -> "Core service is temporarily unavailable";
            case "IDENTITY_ALREADY_LINKED", "IDENTITY_LINK_CONFLICT" -> "Login identity cannot be linked";
            default -> "Authentication failed";
        };
    }
}
