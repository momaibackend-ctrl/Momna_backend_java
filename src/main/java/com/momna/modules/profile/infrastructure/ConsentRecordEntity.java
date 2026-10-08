package com.momna.modules.profile.infrastructure;

import com.momna.modules.profile.domain.ConsentState;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "consent_records", schema = "momna")
public class ConsentRecordEntity {
    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "consent_type", nullable = false)
    private String consentType;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentState state;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(nullable = false)
    private String source;

    protected ConsentRecordEntity() {}

    public ConsentRecordEntity(
        String id, String userId, String consentType, String policyVersion,
        ConsentState state, Instant recordedAt, String source
    ) {
        this.id = id;
        this.userId = userId;
        this.consentType = consentType;
        this.policyVersion = policyVersion;
        this.state = state;
        this.recordedAt = recordedAt;
        this.source = source;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getConsentType() { return consentType; }
    public String getPolicyVersion() { return policyVersion; }
    public ConsentState getState() { return state; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getSource() { return source; }
}
