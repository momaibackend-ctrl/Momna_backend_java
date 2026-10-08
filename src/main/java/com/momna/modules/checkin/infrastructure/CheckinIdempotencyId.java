package com.momna.modules.checkin.infrastructure;

import java.io.Serializable;

public class CheckinIdempotencyId implements Serializable {
    public String userId;
    public String operation;
    public String idempotencyKey;

    public CheckinIdempotencyId() {}

    public CheckinIdempotencyId(String userId, String operation, String idempotencyKey) {
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
    }
}
