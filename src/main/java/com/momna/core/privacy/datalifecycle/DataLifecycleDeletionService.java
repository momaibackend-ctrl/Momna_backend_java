package com.momna.core.privacy.datalifecycle;

import com.momna.core.privacy.*;
import com.momna.core.privacy.datalifecycle.infrastructure.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataLifecycleDeletionService {
    public static final String CONTRACT_VERSION = "data-lifecycle.v1";

    private final DataLifecycleDeletionStore store;
    private final DataLifecycleRetentionService retention;
    private final DataLifecycleOperationRepository operations;
    private final PrivacyPolicyService privacy;
    private final Map<String, DataLifecycleOwnerAdapter> owners;
    private final ObjectProvider<DataLifecycleJobPort> jobs;

    public DataLifecycleDeletionService(
        DataLifecycleDeletionStore store,
        DataLifecycleRetentionService retention,
        DataLifecycleOperationRepository operations,
        PrivacyPolicyService privacy,
        ObjectProvider<DataLifecycleOwnerAdapter> owners,
        ObjectProvider<DataLifecycleJobPort> jobs
    ) {
        this.store = store;
        this.retention = retention;
        this.operations = operations;
        this.privacy = privacy;
        this.owners = owners.orderedStream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(DataLifecycleOwnerAdapter::owner, x -> x)
        );
        this.jobs = jobs;
    }

    @Transactional
    public DataLifecycleDeletionStore.Plan plan(
        String actorUserId,
        String subjectUserId,
        Instant snapshotAt,
        String idempotencyKey,
        String traceId
    ) {
        var existing = store.findPlanByIdempotencyKey(idempotencyKey);
        if (existing != null) return existing;

        var actions = new ArrayList<DataLifecycleDeletionStore.Action>();
        for (var adapter : owners.values().stream()
            .sorted(Comparator.comparing(DataLifecycleOwnerAdapter::owner)).toList()) {
            for (var resource : adapter.inventory(subjectUserId, snapshotAt).stream()
                .sorted(Comparator
                    .comparing(DataLifecycleOwnerAdapter.Resource::resourceType)
                    .thenComparing(DataLifecycleOwnerAdapter.Resource::resourceId)).toList()) {
                authorize(actorUserId, resource, "data-lifecycle.deletion.plan", traceId);

                var evaluation = retention.evaluate(
                    resource.owner(),
                    resource.resourceType(),
                    resource.privacyScope(),
                    resource.retentionAnchorAt(),
                    snapshotAt,
                    null
                );
                var policy = retention.resolvePolicy(
                    resource.owner(), resource.resourceType(), evaluation.policyVersion()
                );

                DeletionAction action;
                String reason;
                if (policy.isLegalHold()) {
                    action = DeletionAction.RETAIN_REQUIRED;
                    reason = "LEGAL_HOLD";
                } else if (!policy.isDeletionAllowed()) {
                    action = DeletionAction.RETAIN_REQUIRED;
                    reason = "DELETION_NOT_ALLOWED";
                } else if (!evaluation.deleteEligible()) {
                    action = DeletionAction.RETAIN_REQUIRED;
                    reason = "RETENTION_REQUIRED";
                } else {
                    action = policy.getDeletionAction();
                    reason = switch (action) {
                        case DELETE -> "DELETE_ELIGIBLE";
                        case ANONYMIZE -> "ANONYMIZE_ELIGIBLE";
                        case ARCHIVE_REQUIRED -> "ARCHIVE_REQUIRED";
                        case RETAIN_REQUIRED -> "RETENTION_REQUIRED";
                    };
                }

                actions.add(new DataLifecycleDeletionStore.Action(
                    resource,
                    action,
                    reason,
                    policy.getPolicyKey(),
                    policy.getPolicyVersion()
                ));
            }
        }
        return store.createPlan(
            subjectUserId, actorUserId, snapshotAt, CONTRACT_VERSION,
            require(idempotencyKey, "idempotencyKey"), require(traceId, "traceId"),
            List.copyOf(actions)
        );
    }

    @Transactional
    public DataLifecycleOperationEntity execute(
        String actorUserId,
        UUID planId,
        String idempotencyKey,
        String traceId
    ) {
        var plan = store.findPlan(planId);
        if (plan == null) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "RESOURCE_NOT_FOUND", "Deletion plan not found"
            );
        }
        if (!plan.actorUserId().equals(actorUserId)) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "FORBIDDEN", "Deletion plan actor mismatch"
            );
        }
        for (var action : plan.actions()) {
            authorize(actorUserId, action.resource(), "data-lifecycle.deletion.execute", traceId);
        }

        var existing = operations.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return existing;

        var operation = new DataLifecycleOperationEntity(
            UUID.randomUUID(),
            plan.subjectUserId(),
            actorUserId,
            DataLifecycleOperationKind.DELETION,
            require(idempotencyKey, "idempotencyKey"),
            require(traceId, "traceId"),
            Instant.now()
        );
        operation.attachDeletionPlan(planId);
        operation = operations.saveAndFlush(operation);

        var queue = jobs.getIfAvailable();
        if (queue == null) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "DEPENDENCY_UNAVAILABLE", "Durable job queue is unavailable"
            );
        }
        queue.enqueue(operation.getOperationId(), operation.getKind(), operation.getTraceId());
        return operation;
    }

    private void authorize(
        String actorUserId,
        DataLifecycleOwnerAdapter.Resource resource,
        String purpose,
        String traceId
    ) {
        privacy.enforce(new PrivacyPolicyService.Request(
            new PrivacyPolicyService.Subject(actorUserId, actorUserId, true, "USER"),
            new PrivacyPolicyService.Resource(
                resource.resourceId(),
                resource.subjectUserId(),
                resource.owner(),
                resource.privacyScope(),
                null,
                resource.privacyScope() != PrivacyScope.PUBLIC_CONTENT
            ),
            PolicyAction.WRITE,
            new PrivacyPolicyService.Context(
                purpose,
                traceId,
                CanonicalPrivacyPolicyService.CURRENT_POLICY_VERSION,
                null,
                null,
                resource.privacyScope(),
                null,
                false
            )
        ));
    }

    private String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
