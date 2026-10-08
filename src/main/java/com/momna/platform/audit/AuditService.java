package com.momna.platform.audit;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final Pattern TECHNICAL = Pattern.compile("[A-Za-z0-9_.:/-]{1,160}");
    private static final Set<String> REGISTERED = Set.of(
        "LIFECYCLE_TRANSITION",
        "CONSENT_CHANGE",
        "MEDICAL_CONFIRM",
        "MEDICAL_EDIT",
        "COUPLE_SHARE",
        "COUPLE_UNLINK",
        "SAFETY_DECISION",
        "DATA_EXPORT",
        "DATA_DELETE",
        "AI_GENERATION_DECISION",
        "LEGACY_TECHNICAL"
    );
    private static final Set<String> FORBIDDEN_METADATA_FRAGMENTS = Set.of(
        "payload", "prompt", "token", "credential", "password", "secret",
        "email", "phone", "medical_content", "private_context"
    );

    private final AuditEventRepository events;
    private final Clock clock = Clock.systemUTC();

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional
    public AuditEventEntity record(Command command) {
        if (!REGISTERED.contains(command.eventType())) {
            throw new IllegalArgumentException("Unregistered audit event type");
        }
        requireTechnical(command.requestId(), "requestId");
        requireTechnical(command.traceId(), "traceId");
        requireTechnical(command.actorRef(), "actorRef");
        requireTechnical(command.subjectRef(), "subjectRef");
        requireTechnical(command.sourceModule(), "sourceModule");
        requireTechnical(command.actionCode(), "actionCode");
        requireTechnical(command.resultCode(), "resultCode");
        validateMetadata(command.metadata());

        var entity = new AuditEventEntity(
            UUID.randomUUID(),
            command.eventType(),
            1,
            clock.instant(),
            command.requestId(),
            command.traceId(),
            command.actorRef(),
            command.subjectRef(),
            command.sourceModule(),
            command.flowInstanceId(),
            command.contextSnapshotId(),
            command.actionCode(),
            command.resultCode(),
            command.policyVersion(),
            command.errorCode(),
            command.errorCategory(),
            command.errorRetryable(),
            command.versions(),
            command.metadata()
        );
        return events.save(entity);
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditEventEntity> byTrace(String traceId) {
        requireTechnical(traceId, "traceId");
        return events.findTop200ByTraceIdOrderByOccurredAtDesc(traceId);
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditEventEntity> bySubject(String subjectRef) {
        requireTechnical(subjectRef, "subjectRef");
        return events.findTop200BySubjectRefOrderByOccurredAtDesc(subjectRef);
    }

    private void validateMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return;
        if (metadata.size() > 32) throw new IllegalArgumentException("Too many audit metadata fields");
        for (var entry : metadata.entrySet()) {
            var key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            if (FORBIDDEN_METADATA_FRAGMENTS.stream().anyMatch(key::contains)) {
                throw new IllegalArgumentException("Sensitive audit metadata field is forbidden");
            }
            var value = String.valueOf(entry.getValue());
            if (value.length() > 512 || value.startsWith("Bearer ")) {
                throw new IllegalArgumentException("Audit metadata value is not a bounded technical value");
            }
        }
    }

    private void requireTechnical(String value, String field) {
        if (value == null || !TECHNICAL.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a bounded technical identifier");
        }
    }

    public record Command(
        String eventType,
        String requestId,
        String traceId,
        String actorRef,
        String subjectRef,
        String sourceModule,
        String flowInstanceId,
        String contextSnapshotId,
        String actionCode,
        String resultCode,
        String policyVersion,
        String errorCode,
        String errorCategory,
        Boolean errorRetryable,
        Map<String, Object> versions,
        Map<String, Object> metadata
    ) {}
}
