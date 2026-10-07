package com.momna.platform.notifications.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_operations", schema = "momna")
public class NotificationOperationEntity {
    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(nullable = false)
    private String operation;

    @Column(name = "entity_ref", nullable = false)
    private String entityRef;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected NotificationOperationEntity() {}

    public NotificationOperationEntity(String idempotencyKey, String operation, String entityRef) {
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.entityRef = entityRef;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getOperation() { return operation; }
    public String getEntityRef() { return entityRef; }
}
