package com.momna.modules.auth.infrastructure;

import com.momna.modules.auth.domain.AuthProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "login_identities",
    schema = "momna",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_subject"})
)
public class LoginIdentityEntity {
    @Id
    @Column(name = "identity_id")
    private String identityId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(name = "verified_email")
    private String verifiedEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoginIdentityEntity() {}

    public LoginIdentityEntity(
        String identityId, String userId, AuthProvider provider,
        String providerSubject, String verifiedEmail, Instant createdAt
    ) {
        this.identityId = identityId;
        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.verifiedEmail = verifiedEmail;
        this.createdAt = createdAt;
    }

    public String getIdentityId() { return identityId; }
    public String getUserId() { return userId; }
    public AuthProvider getProvider() { return provider; }
    public String getProviderSubject() { return providerSubject; }
    public String getVerifiedEmail() { return verifiedEmail; }
    public Instant getCreatedAt() { return createdAt; }
}
