package com.momna.modules.medicalsafety.infrastructure;

import java.io.Serializable;
import java.util.Objects;

public class MedicalSafetyIdempotencyId implements Serializable {
    public String userId;
    public String operation;
    public String idempotencyKey;

    public MedicalSafetyIdempotencyId() {}

    public MedicalSafetyIdempotencyId(String userId, String operation, String idempotencyKey) {
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MedicalSafetyIdempotencyId id && Objects.equals(userId, id.userId)
            && Objects.equals(operation, id.operation) && Objects.equals(idempotencyKey, id.idempotencyKey);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, operation, idempotencyKey); }
}
