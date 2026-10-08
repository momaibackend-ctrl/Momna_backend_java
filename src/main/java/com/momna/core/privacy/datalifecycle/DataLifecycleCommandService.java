package com.momna.core.privacy.datalifecycle;

import com.momna.core.privacy.*;
import com.momna.core.privacy.datalifecycle.infrastructure.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataLifecycleCommandService {
    private final DataLifecycleOperationRepository operations;
    private final DataLifecycleArchiveManifestRepository archiveManifests;
    private final DataLifecycleRetentionService retention;
    private final PrivacyPolicyService privacy;
    private final ObjectProvider<DataLifecycleJobPort> jobs;

    public DataLifecycleCommandService(
        DataLifecycleOperationRepository operations,
        DataLifecycleArchiveManifestRepository archiveManifests,
        DataLifecycleRetentionService retention,
        PrivacyPolicyService privacy,
        ObjectProvider<DataLifecycleJobPort> jobs
    ) {
        this.operations = operations;
        this.archiveManifests = archiveManifests;
        this.retention = retention;
        this.privacy = privacy;
        this.jobs = jobs;
    }

    @Transactional
    public WriteResult requestArchive(
        String actorUserId,
        String subjectUserId,
        String owner,
        String resourceType,
        String resourceId,
        PrivacyScope scope,
        Instant retentionAnchorAt,
        Instant referenceAt,
        String requestTimezone,
        String idempotencyKey,
        String traceId
    ) {
        authorize(actorUserId, subjectUserId, owner, resourceId, scope, "data-lifecycle.archive", traceId);

        var evaluation = retention.evaluate(
            owner, resourceType, scope, retentionAnchorAt, referenceAt, null
        );
        if (evaluation.mustRetain() && !evaluation.archiveEligible()) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "ARCHIVE_NOT_ALLOWED", "Resource is not eligible for archive"
            );
        }

        var operation = new DataLifecycleOperationEntity(
            UUID.randomUUID(),
            subjectUserId,
            actorUserId,
            DataLifecycleOperationKind.ARCHIVE,
            requireIdempotency(idempotencyKey),
            requireText(traceId, "traceId"),
            referenceAt
        );
        operation.attachResource(
            owner,
            resourceType,
            resourceId,
            scope,
            retentionAnchorAt,
            evaluation.policyKey(),
            evaluation.policyVersion()
        );
        captureLocalTime(operation, referenceAt, requestTimezone);
        return persistAndQueue(operation);
    }

    @Transactional
    public WriteResult requestRestore(
        String actorUserId,
        UUID archiveOperationId,
        String requestTimezone,
        String idempotencyKey,
        String traceId
    ) {
        var archive = operations.findById(archiveOperationId)
            .orElseThrow(() -> new DataLifecycleRetentionService.DataLifecycleException(
                "RESOURCE_NOT_FOUND", "Archive operation not found"
            ));
        if (archive.getKind() != DataLifecycleOperationKind.ARCHIVE
            || archive.getState() != DataLifecycleOperationState.SUCCEEDED) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "RESTORE_NOT_ALLOWED", "Archive operation is not restorable"
            );
        }
        if (!archiveManifests.existsById(archiveOperationId)) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "RESOURCE_NOT_FOUND", "Archive manifest not found"
            );
        }

        var policy = retention.resolvePolicy(
            archive.getResourceOwner(),
            archive.getResourceType(),
            archive.getPolicyVersion()
        );
        if (!policy.isRestoreAllowed()) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "RESTORE_NOT_ALLOWED", "Restore is not allowed by retention policy"
            );
        }

        authorize(
            actorUserId,
            archive.getSubjectUserId(),
            archive.getResourceOwner(),
            archive.getResourceId(),
            archive.getPrivacyScope(),
            "data-lifecycle.restore",
            traceId
        );

        var operation = new DataLifecycleOperationEntity(
            UUID.randomUUID(),
            archive.getSubjectUserId(),
            actorUserId,
            DataLifecycleOperationKind.RESTORE,
            requireIdempotency(idempotencyKey),
            requireText(traceId, "traceId"),
            Instant.now()
        );
        operation.attachResource(
            archive.getResourceOwner(),
            archive.getResourceType(),
            archive.getResourceId(),
            archive.getPrivacyScope(),
            archive.getRetentionAnchorAt(),
            policy.getPolicyKey(),
            policy.getPolicyVersion()
        );
        operation.linkRelatedOperation(archiveOperationId);
        captureLocalTime(operation, Instant.now(), requestTimezone);
        return persistAndQueue(operation);
    }

    @Transactional
    public WriteResult requestExport(
        String actorUserId,
        String subjectUserId,
        Instant snapshotAt,
        String requestTimezone,
        String idempotencyKey,
        String traceId
    ) {
        authorize(
            actorUserId,
            subjectUserId,
            "core.privacy",
            "export:" + Integer.toUnsignedString(subjectUserId.hashCode()),
            PrivacyScope.GENERAL_PROFILE,
            "data-lifecycle.export",
            traceId
        );

        var operation = new DataLifecycleOperationEntity(
            UUID.randomUUID(),
            subjectUserId,
            actorUserId,
            DataLifecycleOperationKind.EXPORT,
            requireIdempotency(idempotencyKey),
            requireText(traceId, "traceId"),
            snapshotAt
        );
        captureLocalTime(operation, snapshotAt, requestTimezone);
        return persistAndQueue(operation);
    }

    @Transactional(readOnly = true)
    public DataLifecycleOperationEntity inspect(UUID operationId) {
        return operations.findById(operationId)
            .orElseThrow(() -> new DataLifecycleRetentionService.DataLifecycleException(
                "RESOURCE_NOT_FOUND", "Lifecycle operation not found"
            ));
    }

    private WriteResult persistAndQueue(DataLifecycleOperationEntity operation) {
        var existing = operations.findByIdempotencyKey(operation.getIdempotencyKey()).orElse(null);
        if (existing != null) return new WriteResult(existing, true);

        DataLifecycleOperationEntity saved;
        try {
            saved = operations.saveAndFlush(operation);
        } catch (DataIntegrityViolationException conflict) {
            saved = operations.findByIdempotencyKey(operation.getIdempotencyKey())
                .orElseThrow(() -> conflict);
            return new WriteResult(saved, true);
        }

        var job = jobs.getIfAvailable();
        if (job == null) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "DEPENDENCY_UNAVAILABLE", "Durable job queue is unavailable"
            );
        }
        job.enqueue(saved.getOperationId(), saved.getKind(), saved.getTraceId());
        return new WriteResult(saved, false);
    }

    private void authorize(
        String actorUserId,
        String subjectUserId,
        String domain,
        String resourceId,
        PrivacyScope scope,
        String purpose,
        String traceId
    ) {
        privacy.enforce(new PrivacyPolicyService.Request(
            new PrivacyPolicyService.Subject(actorUserId, actorUserId, true, "USER"),
            new PrivacyPolicyService.Resource(
                resourceId,
                subjectUserId,
                domain,
                scope,
                null,
                scope != PrivacyScope.PUBLIC_CONTENT
            ),
            PolicyAction.WRITE,
            new PrivacyPolicyService.Context(
                purpose,
                traceId,
                CanonicalPrivacyPolicyService.CURRENT_POLICY_VERSION,
                null,
                null,
                scope,
                null,
                false
            )
        ));
    }

    private void captureLocalTime(
        DataLifecycleOperationEntity operation,
        Instant at,
        String timezone
    ) {
        if (timezone == null || timezone.isBlank()) return;
        try {
            var zone = ZoneId.of(timezone);
            operation.captureRequestLocalTime(zone.getId(), at.atZone(zone).toLocalDate());
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid request timezone");
        }
    }

    private String requireIdempotency(String value) {
        var result = requireText(value, "idempotencyKey");
        if (result.length() > 200) throw new IllegalArgumentException("idempotencyKey is too long");
        return result;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    public record WriteResult(
        DataLifecycleOperationEntity operation,
        boolean idempotentReplay
    ) {}
}
