package com.momna.modules.content.infrastructure;

import com.momna.modules.content.ContentType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "content_objects", schema = "momna")
public class ContentObjectEntity {
    @Id
    @Column(name = "content_id")
    private String contentId;

    @Column(name = "content_key", nullable = false, unique = true)
    private String contentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected ContentObjectEntity() {}

    public ContentObjectEntity(String contentId, String contentKey, ContentType contentType, List<String> tags, Instant createdAt) {
        this.contentId = contentId;
        this.contentKey = contentKey;
        this.contentType = contentType;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.createdAt = createdAt;
    }

    public String getContentId() { return contentId; }
    public String getContentKey() { return contentKey; }
    public ContentType getContentType() { return contentType; }
    public List<String> getTags() { return List.copyOf(tags); }
    public Instant getCreatedAt() { return createdAt; }
    public long getRowVersion() { return rowVersion; }
}
