package com.momna.core.context.application;

public class ContextPlatformException extends RuntimeException {
    private final String code;

    public ContextPlatformException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
