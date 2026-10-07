package com.momna.platform.cache;

public class CacheException extends RuntimeException {
    private final String code;

    public CacheException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() { return code; }
}
