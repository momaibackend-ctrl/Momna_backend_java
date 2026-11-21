package com.momna.modules.myday.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "my_day_idempotency", schema = "momna")
@IdClass(MyDayIdempotencyId.class)
public class MyDayIdempotencyEntity {
    @Id @Column(name = "user_id") private String userId;
    @Id private String operation;
    @Id @Column(name = "idempotency_key") private String idempotencyKey;

    @Column(name = "fingerprint_sha256", nullable = false, length = 64)
    private String fingerprintSha256;

    @Column(name = "result_my_day_id", nullable = false)
    private String resultMyDayId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MyDayIdempotencyEntity() {}

    public MyDayIdempotencyEntity(String userId, String operation, String idempotencyKey,
                                  String fingerprintSha256, String resultMyDayId, Instant createdAt) {
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.fingerprintSha256 = fingerprintSha256;
        this.resultMyDayId = resultMyDayId;
        this.createdAt = createdAt;
    }

    public String getFingerprintSha256() { return fingerprintSha256; }
    public String getResultMyDayId() { return resultMyDayId; }
}
