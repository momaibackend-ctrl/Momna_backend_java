package com.momna.modules.myday.application;

/** Typed My Day failure; the code is the public error code. */
public class MyDayException extends RuntimeException {
    private final String code;

    public MyDayException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
