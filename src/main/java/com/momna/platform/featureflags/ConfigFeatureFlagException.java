package com.momna.platform.featureflags;

public class ConfigFeatureFlagException extends RuntimeException {
    private final String code;

    public ConfigFeatureFlagException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
