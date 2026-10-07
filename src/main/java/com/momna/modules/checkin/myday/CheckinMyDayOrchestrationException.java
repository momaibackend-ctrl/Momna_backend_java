package com.momna.modules.checkin.myday;

public class CheckinMyDayOrchestrationException extends RuntimeException {
    private final String code;

    public CheckinMyDayOrchestrationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
