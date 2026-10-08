package com.momna.modules.auth.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auth_refresh_history", schema = "momna")
public class AuthRefreshHistoryEntity {
    @Id
    @Column(name = "refresh_hash")
    private String refreshHash;

    @Column(name = "family_id", nullable = false)
    private String familyId;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    protected AuthRefreshHistoryEntity() {}

    public AuthRefreshHistoryEntity(String refreshHash, String familyId, Instant usedAt) {
        this.refreshHash = refreshHash;
        this.familyId = familyId;
        this.usedAt = usedAt;
    }

    public String getRefreshHash() { return refreshHash; }
    public String getFamilyId() { return familyId; }
    public Instant getUsedAt() { return usedAt; }
}
