package com.momna.shared.api;

import com.momna.modules.auth.application.AuthException;
import com.momna.modules.billing.application.BillingRestoreService;
import com.momna.modules.checkin.application.CheckinException;
import com.momna.modules.flow.application.FlowException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
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

    @ExceptionHandler(CheckinException.class)
    ResponseEntity<ApiError> checkin(CheckinException ex) {
        var status = switch (ex.code()) {
            case "CHECKIN_SESSION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CHECKIN_NOT_ELIGIBLE" -> HttpStatus.FORBIDDEN;
            case "CHECKIN_ITEM_UNKNOWN", "CHECKIN_ITEM_NOT_APPLICABLE",
                 "CHECKIN_VALUE_INVALID", "SAFETY_VALUE_INVALID" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "DEPENDENCY_UNAVAILABLE", "CHECKIN_DEFINITION_UNAVAILABLE",
                 "MY_DAY_DEPENDENCY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "CHECKIN_WINDOW_NOT_OPEN", "CHECKIN_WINDOW_CLOSED",
                 "CHECKIN_SESSION_FINALIZED", "CHECKIN_ALREADY_SUBMITTED",
                 "CHECKIN_ALREADY_AUTO_FINALIZED", "CHECKIN_SESSION_CONSUMED",
                 "CHECKIN_DEFINITION_VERSION_MISMATCH", "SAFETY_CLARIFICATION_REQUIRED",
                 "SAFETY_CLARIFICATION_NOT_CURRENT", "SAFETY_STATE_BLOCKING",
                 "SAFETY_ROUTE_VERSION_MISMATCH", "STALE_REVISION",
                 "IDEMPOTENCY_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(ApiError.of(ex.code(), switch (status) {
            case NOT_FOUND -> "Check-in session not found";
            case FORBIDDEN -> "Check-in is not available for the current lifecycle state";
            case UNPROCESSABLE_ENTITY -> "Check-in request contains an invalid answer";
            case SERVICE_UNAVAILABLE -> "Check-in dependency is temporarily unavailable";
            case CONFLICT -> "Check-in request conflicts with the current session state";
            default -> "Unexpected server error";
        }));
    }

    @ExceptionHandler(FlowException.class)
    ResponseEntity<ApiError> flow(FlowException ex) {
        var status = switch (ex.code()) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "VALIDATION" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "STALE_VERSION", "CONFLICT", "INVALID_TRANSITION" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        var publicCode = switch (ex.code()) {
            case "NOT_FOUND" -> "FLOW_NOT_FOUND";
            case "FORBIDDEN" -> "FLOW_FORBIDDEN";
            case "VALIDATION" -> "INVALID_FIELD_VALUE";
            case "STALE_VERSION" -> "FLOW_DEFINITION_VERSION_MISMATCH";
            case "CONFLICT" -> "STALE_REVISION";
            case "INVALID_TRANSITION" -> "FLOW_INVALID_TRANSITION";
            default -> "INTERNAL_ERROR";
        };
        return ResponseEntity.status(status).body(ApiError.of(publicCode, switch (status) {
            case NOT_FOUND -> "Flow instance not found";
            case FORBIDDEN -> "Flow instance is not accessible";
            case UNPROCESSABLE_ENTITY -> "Flow request is invalid";
            case CONFLICT -> "Flow mutation conflicts with authoritative state";
            default -> "Unexpected server error";
        }));
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

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("NOT_FOUND", "Resource not found"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unhandled request failure", ex);
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
