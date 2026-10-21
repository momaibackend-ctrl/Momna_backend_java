package com.momna.platform.safety;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Internal audit trail of user-initiated corrections to sensitive safety facts. */
@Entity
@Table(name = "medical_safety_fact_revisions", schema = "momna")
public class MedicalSafetyFactRevisionEntity {
    @Id
    @Column(name = "revision_id")
    private String revisionId;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(name = "route_id", nullable = false)
    private String routeId;
    @Column(name = "session_id")
    private String sessionId;
    @Column(name = "fact_code", nullable = false)
    private String factCode;
    @Column(name = "previous_answer", nullable = false)
    private String previousAnswer;
    @Column(name = "new_answer", nullable = false)
    private String newAnswer;
    @Column(name = "revised_at", nullable = false)
    private Instant revisedAt;
    protected MedicalSafetyFactRevisionEntity() {}

    public MedicalSafetyFactRevisionEntity(String userId, String routeId, String sessionId,
        String factCode, String previousAnswer, String newAnswer, Instant at) {
        this.revisionId = UUID.randomUUID().toString();
        this.userId = userId;
        this.routeId = routeId;
        this.sessionId = sessionId;
        this.factCode = factCode;
        this.previousAnswer = previousAnswer;
        this.newAnswer = newAnswer;
        this.revisedAt = at;
    }
}
