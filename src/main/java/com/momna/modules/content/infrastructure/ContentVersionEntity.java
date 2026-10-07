package com.momna.modules.content.infrastructure;

import com.momna.modules.content.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "content_versions", schema = "momna")
@IdClass(ContentVersionId.class)
public class ContentVersionEntity {
    @Id
    @Column(name = "content_id")
    private String contentId;

    @Id
    private int version;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private ContentSourceType sourceType;

    @Column(name = "source_reference")
    private String sourceReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String,Object>> variants;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected ContentVersionEntity() {}

    public ContentVersionEntity(
        String contentId, int version, int schemaVersion, ContentType contentType,
        ContentStatus status, ContentSourceType sourceType, String sourceReference,
        List<Map<String,Object>> variants, Instant createdAt
    ) {
        this.contentId = contentId;
        this.version = version;
        this.schemaVersion = schemaVersion;
        this.contentType = contentType;
        this.status = status;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
        this.variants = variants == null ? List.of() : List.copyOf(variants);
        this.createdAt = createdAt;
    }

    public String getContentId() { return contentId; }
    public int getVersion() { return version; }
    public int getSchemaVersion() { return schemaVersion; }
    public ContentType getContentType() { return contentType; }
    public ContentStatus getStatus() { return status; }
    public ContentSourceType getSourceType() { return sourceType; }
    public String getSourceReference() { return sourceReference; }
    public List<Map<String,Object>> getVariants() { return List.copyOf(variants); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public long getRowVersion() { return rowVersion; }

    public void publish(Instant at) {
        this.status = ContentStatus.PUBLISHED;
        this.publishedAt = at;
    }

    public void unpublish() {
        this.status = ContentStatus.UNPUBLISHED;
    }
}
