package com.momna.core.privacy.datalifecycle;

import com.momna.core.privacy.PrivacyScope;
import com.momna.core.privacy.datalifecycle.infrastructure.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DataLifecycleRetentionService {
    private final DataLifecyclePolicyRepository policies;

    public DataLifecycleRetentionService(DataLifecyclePolicyRepository policies) {
        this.policies = policies;
    }

    public DataLifecyclePolicyEntity resolvePolicy(String owner, String resourceType, Integer version) {
        if (owner == null || owner.isBlank() || resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("owner and resourceType are required");
        }

        if (version != null) {
            return policies.findById(new DataLifecyclePolicyId(owner + ":" + resourceType, version))
                .orElseGet(() -> policies.findByOwnerAndResourceTypeOrderByPolicyVersionDesc(owner, resourceType)
                    .stream()
                    .filter(x -> x.getPolicyVersion() == version)
                    .findFirst()
                    .orElseThrow(() -> new DataLifecycleException("POLICY_NOT_FOUND", "Retention policy not found")));
        }

        return policies.findByOwnerAndResourceTypeOrderByPolicyVersionDesc(owner, resourceType)
            .stream()
            .findFirst()
            .orElseThrow(() -> new DataLifecycleException("POLICY_NOT_FOUND", "Retention policy not found"));
    }

    public RetentionEvaluation evaluate(
        String owner,
        String resourceType,
        PrivacyScope privacyScope,
        Instant retentionAnchorAt,
        Instant referenceAt,
        Integer policyVersion
    ) {
        if (retentionAnchorAt == null || referenceAt == null) {
            throw new IllegalArgumentException("retentionAnchorAt and referenceAt are required");
        }

        var policy = resolvePolicy(owner, resourceType, policyVersion);
        if (policy.getPrivacyScope() != privacyScope) {
            throw new DataLifecycleException("VALIDATION_ERROR", "Retention policy privacy scope mismatch");
        }

        var retainUntil = policy.getRetentionMode() == RetentionMode.WINDOW
            && policy.getRetainForSeconds() != null
            ? retentionAnchorAt.plusSeconds(policy.getRetainForSeconds())
            : null;

        var archiveEligibleAt = policy.getArchiveAfterSeconds() == null
            ? null : retentionAnchorAt.plusSeconds(policy.getArchiveAfterSeconds());

        var deleteEligibleAt = policy.getDeleteAfterSeconds() == null
            ? null : retentionAnchorAt.plusSeconds(policy.getDeleteAfterSeconds());

        var mustRetain = policy.isLegalHold()
            || policy.getRetentionMode() == RetentionMode.INDEFINITE
            || (retainUntil != null && referenceAt.isBefore(retainUntil));

        var archiveEligible = policy.isArchiveAllowed()
            && archiveEligibleAt != null
            && !referenceAt.isBefore(archiveEligibleAt);

        var deleteEligible = policy.isDeletionAllowed()
            && !mustRetain
            && deleteEligibleAt != null
            && !referenceAt.isBefore(deleteEligibleAt);

        var state = mustRetain
            ? RetentionState.RETAIN_REQUIRED
            : deleteEligible
                ? RetentionState.DELETE_ELIGIBLE
                : archiveEligible
                    ? RetentionState.ARCHIVE_ELIGIBLE
                    : RetentionState.ACTIVE;

        return new RetentionEvaluation(
            policy.getPolicyKey(),
            policy.getPolicyVersion(),
            retainUntil,
            archiveEligibleAt,
            deleteEligibleAt,
            mustRetain,
            archiveEligible,
            deleteEligible,
            state
        );
    }

    public record RetentionEvaluation(
        String policyKey,
        int policyVersion,
        Instant retainUntil,
        Instant archiveEligibleAt,
        Instant deleteEligibleAt,
        boolean mustRetain,
        boolean archiveEligible,
        boolean deleteEligible,
        RetentionState state
    ) {}

    public static class DataLifecycleException extends RuntimeException {
        private final String code;

        public DataLifecycleException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() { return code; }
    }
}
