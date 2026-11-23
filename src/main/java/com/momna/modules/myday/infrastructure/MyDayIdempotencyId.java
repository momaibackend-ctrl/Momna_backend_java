package com.momna.modules.myday.infrastructure;

import java.io.Serializable;
import java.util.Objects;

public class MyDayIdempotencyId implements Serializable {
    public String userId;
    public String operation;
    public String idempotencyKey;

    public MyDayIdempotencyId() {}

    public MyDayIdempotencyId(String userId, String operation, String idempotencyKey) {
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
    }

    @Override public boolean equals(Object other) {
        return other instanceof MyDayIdempotencyId id && Objects.equals(userId, id.userId)
            && Objects.equals(operation, id.operation) && Objects.equals(idempotencyKey, id.idempotencyKey);
    }

    @Override public int hashCode() { return Objects.hash(userId, operation, idempotencyKey); }
}
