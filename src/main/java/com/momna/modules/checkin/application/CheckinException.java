package com.momna.modules.checkin.application;

public class CheckinException extends RuntimeException {
    private final String code;

    public CheckinException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
