package com.momna.platform.audit;

import com.momna.platform.observability.StructuredObservabilityService;
import com.momna.platform.observability.TelemetryContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LegacyAuditBridge {
    private final AuditService audit;
    private final StructuredObservabilityService observability;

    public LegacyAuditBridge(
        AuditService audit,
        StructuredObservabilityService observability
    ) {
        this.audit = audit;
        this.observability = observability;
    }

    public AuditEventEntity record(
        String action,
        String actorRef,
        String subjectRef
    ) {
        var id = UUID.randomUUID().toString();
        var eventType = legacyEventType(action);

        var recorded = audit.record(new AuditService.Command(
            eventType,
            id,
            id,
            actorRef,
            subjectRef,
            "legacy-audit-bridge",
            null,
            null,
            action,
            "RECORDED",
            null,
            null,
            null,
            null,
            Map.of(),
            Map.of("compatibility_bridge", "legacy")
        ));

        observability.emit(
            "audit.event.recorded",
            "INFO",
            new TelemetryContext(
                id,
                id,
                "platform.audit",
                null,
                null,
                null,
                Map.of("schema", "v1")
            ),
            Map.of(
                "audit_event_id", recorded.getEventId().toString(),
                "audit_event_type", recorded.getEventType(),
                "action_code", action,
                "result_code", "RECORDED",
                "schema_version", "1"
            ),
            null
        );

        return recorded;
    }

    private String legacyEventType(String action) {
        return switch (action) {
            case "consent.granted", "consent.withdrawn" -> "CONSENT_CHANGE";
            case "lifecycle.transition.confirmed" -> "LIFECYCLE_TRANSITION";
            case "medical.confirmed" -> "MEDICAL_CONFIRM";
            case "medical.edited" -> "MEDICAL_EDIT";
            case "couple.shared" -> "COUPLE_SHARE";
            case "couple.unlinked" -> "COUPLE_UNLINK";
            case "safety.decision" -> "SAFETY_DECISION";
            case "data.export" -> "DATA_EXPORT";
            case "data.delete" -> "DATA_DELETE";
            default -> "LEGACY_TECHNICAL";
        };
    }
}
