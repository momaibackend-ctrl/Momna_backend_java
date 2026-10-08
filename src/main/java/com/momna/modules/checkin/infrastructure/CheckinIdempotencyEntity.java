package com.momna.modules.checkin.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "checkin_idempotency", schema = "momna")
@IdClass(CheckinIdempotencyId.class)
public class CheckinIdempotencyEntity {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    private String operation;

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "fingerprint_sha256", nullable = false, length = 64)
    private String fingerprintSha256;

    @Column(name = "result_session_id", nullable = false)
    private String resultSessionId;

    @Column(name = "result_revision", nullable = false)
    private long resultRevision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheckinIdempotencyEntity() {}

    public CheckinIdempotencyEntity(
        String userId,
        String operation,
        String idempotencyKey,
        String fingerprintSha256,
        String resultSessionId,
        long resultRevision,
        Instant createdAt
    ) {
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.fingerprintSha256 = fingerprintSha256;
        this.resultSessionId = resultSessionId;
        this.resultRevision = resultRevision;
        this.createdAt = createdAt;
    }

    public String getFingerprintSha256() { return fingerprintSha256; }
    public String getResultSessionId() { return resultSessionId; }
    public long getResultRevision() { return resultRevision; }
}
