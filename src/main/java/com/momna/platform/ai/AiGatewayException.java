package com.momna.platform.ai;

public class AiGatewayException extends RuntimeException {
    private final AiErrorCode code;

    public AiGatewayException(AiErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AiGatewayException(AiErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AiErrorCode code() { return code; }
}
