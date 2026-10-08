package com.momna.modules.auth.infrastructure;

import com.momna.modules.auth.domain.AuthAccountStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auth_accounts", schema = "momna")
public class AuthAccountEntity {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthAccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    protected AuthAccountEntity() {}

    public AuthAccountEntity(String userId, AuthAccountStatus status, Instant createdAt) {
        this.userId = userId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getUserId() { return userId; }
    public AuthAccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDisabledAt() { return disabledAt; }
}
