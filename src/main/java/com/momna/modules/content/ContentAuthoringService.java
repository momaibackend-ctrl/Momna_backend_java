package com.momna.modules.content;

import com.momna.modules.content.infrastructure.*;
import com.momna.platform.cache.ExpiringCache;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentAuthoringService {
    private final ContentObjectRepository objects;
    private final ContentVersionRepository versions;
    private final ContentOperationRepository operations;
    private final ObjectProvider<ExpiringCache> cache;

    public ContentAuthoringService(
        ContentObjectRepository objects,
        ContentVersionRepository versions,
        ContentOperationRepository operations,
        ObjectProvider<ExpiringCache> cache
    ) {
        this.objects = objects;
        this.versions = versions;
        this.operations = operations;
        this.cache = cache;
    }

    @Transactional
    public WriteResult createDraft(
        String contentId,
        String contentKey,
        ContentType contentType,
        List<String> tags,
        int version,
        int schemaVersion,
        ContentSourceType sourceType,
        String sourceReference,
        List<Map<String,Object>> variants,
        String idempotencyKey
    ) {
        requireIdempotency(idempotencyKey);
        var replay = replay(idempotencyKey, "CREATE_DRAFT");
        if (replay != null) return replay;

        validateIdentity(contentId, contentKey);
        validateVariants(contentType, sourceType, variants);

        var object = objects.findById(contentId).orElse(null);
        if (object == null) {
            object = objects.saveAndFlush(new ContentObjectEntity(
                contentId, contentKey, contentType, tags, Instant.now()
            ));
        } else if (!object.getContentKey().equals(contentKey) || object.getContentType() != contentType) {
            throw new ContentResolutionService.ContentException(
                "INVALID_CONTENT", "Content identity/type mismatch"
            );
        }

        var entity = new ContentVersionEntity(
            contentId,
            version,
            schemaVersion,
            contentType,
            ContentStatus.DRAFT,
            sourceType,
            sourceReference,
            variants,
            Instant.now()
        );

        try {
            entity = versions.saveAndFlush(entity);
            operations.saveAndFlush(new ContentOperationEntity(
                idempotencyKey, contentId, version, "CREATE_DRAFT", ContentStatus.DRAFT
            ));
        } catch (DataIntegrityViolationException conflict) {
            var existing = replay(idempotencyKey, "CREATE_DRAFT");
            if (existing != null) return existing;
            throw conflict;
        }
        return new WriteResult(entity, false);
    }

    @Transactional
    public WriteResult publish(
        String contentId,
        int version,
        long expectedRowVersion,
        String idempotencyKey,
        Instant publishedAt
    ) {
        requireIdempotency(idempotencyKey);
        var replay = replay(idempotencyKey, "PUBLISH");
        if (replay != null) return replay;

        var entity = get(contentId, version);
        if (entity.getRowVersion() != expectedRowVersion) {
            throw new ContentResolutionService.ContentException(
                "VERSION_CONFLICT", "Content version conflict"
            );
        }
        validateVariants(entity.getContentType(), entity.getSourceType(), entity.getVariants());
        entity.publish(publishedAt);
        entity = versions.saveAndFlush(entity);
        operations.saveAndFlush(new ContentOperationEntity(
            idempotencyKey, contentId, version, "PUBLISH", ContentStatus.PUBLISHED
        ));
        invalidateCache();
        return new WriteResult(entity, false);
    }

    @Transactional
    public WriteResult unpublish(
        String contentId,
        int version,
        long expectedRowVersion,
        String idempotencyKey
    ) {
        requireIdempotency(idempotencyKey);
        var replay = replay(idempotencyKey, "UNPUBLISH");
        if (replay != null) return replay;

        var entity = get(contentId, version);
        if (entity.getRowVersion() != expectedRowVersion) {
            throw new ContentResolutionService.ContentException(
                "VERSION_CONFLICT", "Content version conflict"
            );
        }
        entity.unpublish();
        entity = versions.saveAndFlush(entity);
        operations.saveAndFlush(new ContentOperationEntity(
            idempotencyKey, contentId, version, "UNPUBLISH", ContentStatus.UNPUBLISHED
        ));
        invalidateCache();
        return new WriteResult(entity, false);
    }

    private WriteResult replay(String idempotencyKey, String expectedOperation) {
        var previous = operations.findById(idempotencyKey).orElse(null);
        if (previous == null) return null;
        if (!previous.getOperation().equals(expectedOperation)) {
            throw new ContentResolutionService.ContentException(
                "VALIDATION_ERROR", "Content idempotency key reused for another operation"
            );
        }
        var version = versions.findById(new ContentVersionId(
            previous.getContentId(), previous.getContentVersion()
        )).orElseThrow(() -> new ContentResolutionService.ContentException(
            "VERSION_NOT_FOUND", "Content version from idempotency ledger is missing"
        ));
        return new WriteResult(version, true);
    }

    private ContentVersionEntity get(String contentId, int version) {
        return versions.findById(new ContentVersionId(contentId, version))
            .orElseThrow(() -> new ContentResolutionService.ContentException(
                "VERSION_NOT_FOUND", "Content version not found"
            ));
    }

    private void validateIdentity(String contentId, String contentKey) {
        if (contentId == null || !contentId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{2,119}")) {
            throw new IllegalArgumentException("Invalid content id");
        }
        if (contentKey == null || !contentKey.matches("[a-z0-9][a-z0-9._-]{2,159}")) {
            throw new IllegalArgumentException("Invalid content key");
        }
    }

    private void validateVariants(
        ContentType contentType,
        ContentSourceType sourceType,
        List<Map<String,Object>> variants
    ) {
        if (variants == null || variants.isEmpty()) {
            throw new ContentResolutionService.ContentException(
                "INVALID_CONTENT", "Content version must contain variants"
            );
        }
        var ids = new HashSet<String>();
        for (var variant : variants) {
            var id = String.valueOf(variant.get("id"));
            if (!ids.add(id)) {
                throw new ContentResolutionService.ContentException(
                    "INVALID_CONTENT", "Content variant ids must be unique"
                );
            }
            if (sourceType == ContentSourceType.AI_GENERATED
                && !(variant.get("aiProvenance") instanceof Map<?,?>)) {
                throw new ContentResolutionService.ContentException(
                    "INVALID_CONTENT", "AI-generated content requires AI provenance"
                );
            }
            if (contentType == ContentType.AUDIO) {
                var payload = variant.get("payload");
                if (!(payload instanceof Map<?,?> p)
                    || !String.valueOf(p.get("contentTypeValue")).startsWith("audio/")) {
                    throw new ContentResolutionService.ContentException(
                        "UNSUPPORTED_CONTENT_TYPE", "Audio content requires audio/* payload"
                    );
                }
            }
        }
    }

    private void invalidateCache() {
        var value = cache.getIfAvailable();
        if (value != null) value.invalidateNamespace("content-resolution", "v1");
    }

    private void requireIdempotency(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException("Valid content idempotency key is required");
        }
    }

    public record WriteResult(ContentVersionEntity version, boolean idempotentReplay) {}
}
