package com.momna.modules.flow.application;

public class FlowException extends RuntimeException {
    private final String code;

    public FlowException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
