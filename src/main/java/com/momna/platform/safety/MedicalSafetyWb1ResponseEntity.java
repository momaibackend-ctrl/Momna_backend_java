package com.momna.platform.safety;

import jakarta.persistence.*;
import java.time.Instant;

/** Single non-recurring, optional emotional wellbeing card after birth. */
@Entity
@Table(name = "medical_safety_wb1_responses", schema = "momna")
public class MedicalSafetyWb1ResponseEntity {
    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(name = "answer", nullable = false)
    private String answer;
    @Column(name = "catalog_version", nullable = false)
    private String catalogVersion;
    @Column(name = "responded_at", nullable = false)
    private Instant respondedAt;

    protected MedicalSafetyWb1ResponseEntity() {}

    public MedicalSafetyWb1ResponseEntity(String userId, String answer, Instant at) {
        this.userId = userId;
        this.answer = answer;
        this.respondedAt = at;
        this.catalogVersion = MedicalSafetyCatalog.VERSION;
    }

    public String getUserId() { return userId; }
    public String getAnswer() { return answer; }
    public Instant getRespondedAt() { return respondedAt; }
}
