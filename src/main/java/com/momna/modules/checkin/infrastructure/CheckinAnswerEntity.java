package com.momna.modules.checkin.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "checkin_answers", schema = "momna")
@IdClass(CheckinAnswerId.class)
public class CheckinAnswerEntity {
    @Id
    @Column(name = "session_id")
    private String sessionId;

    @Id
    @Column(name = "item_code")
    private String itemCode;

    @Column(nullable = false)
    private int value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CheckinAnswerEntity() {}

    public CheckinAnswerEntity(String sessionId, String itemCode, int value, Instant updatedAt) {
        if (value < 0 || value > 2) throw new IllegalArgumentException("Check-in value must be 0, 1 or 2");
        this.sessionId = sessionId;
        this.itemCode = itemCode;
        this.value = value;
        this.updatedAt = updatedAt;
    }

    public String getSessionId() { return sessionId; }
    public String getItemCode() { return itemCode; }
    public int getValue() { return value; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void changeTo(int value, Instant at) {
        if (value < 0 || value > 2) throw new IllegalArgumentException("Check-in value must be 0, 1 or 2");
        this.value = value;
        this.updatedAt = at;
    }
}
