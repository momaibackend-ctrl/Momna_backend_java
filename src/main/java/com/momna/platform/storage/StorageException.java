package com.momna.platform.storage;

public class StorageException extends RuntimeException {
    private final String code;

    public StorageException(String code, String message) {
        super(message);
        this.code = code;
    }

    public StorageException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() { return code; }
}
