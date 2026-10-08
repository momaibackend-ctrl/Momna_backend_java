package com.momna.platform.ai;

public class AiProviderException extends RuntimeException {
    private final AiFailureCategory category;

    public AiProviderException(AiFailureCategory category, String message) {
        super(message);
        this.category = category;
    }

    public AiProviderException(AiFailureCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public AiFailureCategory category() { return category; }
}
