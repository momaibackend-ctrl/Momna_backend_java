package com.momna.platform.localization;

public class LocalizationException extends IllegalArgumentException {
    private final String code;

    public LocalizationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
