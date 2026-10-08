package com.momna.modules.auth.infrastructure;

import com.momna.modules.auth.domain.AuthSessionStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auth_sessions", schema = "momna")
public class AuthSessionEntity {
    @Id
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "family_id", nullable = false)
    private String familyId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "access_hash", nullable = false, unique = true)
    private String accessHash;

    @Column(name = "refresh_hash", nullable = false, unique = true)
    private String refreshHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "authenticated_at", nullable = false)
    private Instant authenticatedAt;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "refresh_expires_at", nullable = false)
    private Instant refreshExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthSessionStatus status;

    @Column(name = "device_label")
    private String deviceLabel;

    @Column(name = "replaced_by_session_id")
    private String replacedBySessionId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthSessionEntity() {}

    public AuthSessionEntity(
        String sessionId, String familyId, String userId, String accessHash, String refreshHash,
        Instant createdAt, Instant authenticatedAt, Instant accessExpiresAt, Instant refreshExpiresAt,
        AuthSessionStatus status, String deviceLabel
    ) {
        this.sessionId = sessionId;
        this.familyId = familyId;
        this.userId = userId;
        this.accessHash = accessHash;
        this.refreshHash = refreshHash;
        this.createdAt = createdAt;
        this.authenticatedAt = authenticatedAt;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.status = status;
        this.deviceLabel = deviceLabel;
    }

    public String getSessionId() { return sessionId; }
    public String getFamilyId() { return familyId; }
    public String getUserId() { return userId; }
    public String getAccessHash() { return accessHash; }
    public String getRefreshHash() { return refreshHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAuthenticatedAt() { return authenticatedAt; }
    public Instant getAccessExpiresAt() { return accessExpiresAt; }
    public Instant getRefreshExpiresAt() { return refreshExpiresAt; }
    public AuthSessionStatus getStatus() { return status; }
    public String getDeviceLabel() { return deviceLabel; }
    public String getReplacedBySessionId() { return replacedBySessionId; }
    public Instant getRevokedAt() { return revokedAt; }

    public void revoke(Instant at, String replacementId) {
        this.status = AuthSessionStatus.REVOKED;
        this.revokedAt = at;
        this.replacedBySessionId = replacementId;
    }
}
