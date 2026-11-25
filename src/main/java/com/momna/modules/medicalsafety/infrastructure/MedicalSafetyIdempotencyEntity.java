package com.momna.modules.medicalsafety.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The stored response of one global safety action, replayed for the same key and request. */
@Entity
@Table(name = "medical_safety_idempotency", schema = "momna")
@IdClass(MedicalSafetyIdempotencyId.class)
public class MedicalSafetyIdempotencyEntity {
    @Id
    @Column(name = "user_id")
    private String userId;
    @Id
    @Column(name = "operation")
    private String operation;
    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @Column(name = "fingerprint_sha256", nullable = false, length = 64)
    private String fingerprintSha256;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response", nullable = false, columnDefinition = "jsonb")
    private String response;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MedicalSafetyIdempotencyEntity() {}

    public MedicalSafetyIdempotencyEntity(String userId, String operation, String idempotencyKey, String fingerprint,
        String response, Instant at) {
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.fingerprintSha256 = fingerprint;
        this.response = response;
        this.createdAt = at;
    }

    public String getFingerprintSha256() { return fingerprintSha256; }
    public String getResponse() { return response; }
}
