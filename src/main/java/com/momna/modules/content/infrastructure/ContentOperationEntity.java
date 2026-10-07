package com.momna.modules.content.infrastructure;

import com.momna.modules.content.ContentStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content_operations", schema = "momna")
public class ContentOperationEntity {
    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "content_id", nullable = false)
    private String contentId;

    @Column(name = "content_version", nullable = false)
    private int contentVersion;

    @Column(nullable = false)
    private String operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false)
    private ContentStatus resultStatus;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ContentOperationEntity() {}

    public ContentOperationEntity(
        String idempotencyKey,
        String contentId,
        int contentVersion,
        String operation,
        ContentStatus resultStatus
    ) {
        this.idempotencyKey = idempotencyKey;
        this.contentId = contentId;
        this.contentVersion = contentVersion;
        this.operation = operation;
        this.resultStatus = resultStatus;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getContentId() { return contentId; }
    public int getContentVersion() { return contentVersion; }
    public String getOperation() { return operation; }
    public ContentStatus getResultStatus() { return resultStatus; }
}
