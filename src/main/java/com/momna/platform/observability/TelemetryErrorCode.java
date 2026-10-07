package com.momna.platform.observability;

public enum TelemetryErrorCode {
    VALIDATION_ERROR(TelemetryErrorCategory.VALIDATION, false),
    UNAUTHENTICATED(TelemetryErrorCategory.AUTHENTICATION, false),
    FORBIDDEN(TelemetryErrorCategory.AUTHORIZATION, false),
    NOT_FOUND(TelemetryErrorCategory.NOT_FOUND, false),
    VERSION_CONFLICT(TelemetryErrorCategory.CONFLICT, false),
    TIMEOUT(TelemetryErrorCategory.TIMEOUT, true),
    RATE_LIMITED(TelemetryErrorCategory.RATE_LIMIT, true),
    DEPENDENCY_UNAVAILABLE(TelemetryErrorCategory.DEPENDENCY, true),
    RETRY_EXHAUSTED(TelemetryErrorCategory.RETRY_EXHAUSTED, false),
    PRIVACY_DENIED(TelemetryErrorCategory.PRIVACY, false),
    SAFETY_BLOCKED(TelemetryErrorCategory.SAFETY, false),
    AI_OUTPUT_INVALID(TelemetryErrorCategory.AI_VALIDATION, false),
    INTERNAL_ERROR(TelemetryErrorCategory.INTERNAL, false);

    private final TelemetryErrorCategory category;
    private final boolean retryable;

    TelemetryErrorCode(TelemetryErrorCategory category, boolean retryable) {
        this.category = category;
        this.retryable = retryable;
    }

    public TelemetryErrorCategory category() { return category; }
    public boolean retryable() { return retryable; }
}
