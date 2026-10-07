package com.momna.modules.auth.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auth_email_challenges", schema = "momna")
public class EmailChallengeEntity {
    @Id
    @Column(name = "challenge_id")
    private String challengeId;

    @Column(name = "email_hash", nullable = false)
    private String emailHash;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected EmailChallengeEntity() {}

    public EmailChallengeEntity(
        String challengeId, String emailHash, String codeHash, Instant createdAt, Instant expiresAt
    ) {
        this.challengeId = challengeId;
        this.emailHash = emailHash;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getChallengeId() { return challengeId; }
    public String getEmailHash() { return emailHash; }
    public String getCodeHash() { return codeHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }

    public boolean consume(String expectedEmailHash, String expectedCodeHash, Instant now) {
        if (consumedAt != null || !now.isBefore(expiresAt)) return false;
        if (!emailHash.equals(expectedEmailHash) || !codeHash.equals(expectedCodeHash)) return false;
        consumedAt = now;
        return true;
    }
}
