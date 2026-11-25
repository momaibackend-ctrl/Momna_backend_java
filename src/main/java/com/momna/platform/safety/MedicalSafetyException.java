package com.momna.platform.safety;

/** A typed refusal of a safety action; the code is part of the API contract. */
public class MedicalSafetyException extends RuntimeException {
    private final String code;

    public MedicalSafetyException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
