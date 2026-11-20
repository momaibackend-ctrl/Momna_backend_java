package com.momna.modules.checkin.definition;

/** Typed failure of the check-in safety route catalog. */
public class CheckinSafetyCatalogException extends RuntimeException {
    public enum Code {
        SOURCE_MISSING,
        SOURCE_INVALID,
        VERSION_NOT_FOUND,
        UNKNOWN_ROUTE,
        UNKNOWN_ITEM,
        DUPLICATE_ROUTE,
        DUPLICATE_BINDING,
        ZERO_WEIGHT_BINDING,
        INCOMPLETE_SAFETY_COVERAGE,
        INVALID_CONTRIBUTION
    }

    private final Code code;

    public CheckinSafetyCatalogException(Code code, String message) {
        this(code, message, null);
    }

    public CheckinSafetyCatalogException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() { return code; }
}
