package com.momna.core.privacy;

import java.util.Set;

public interface PrivacyPolicyService {
    Decision decide(Request request);

    default Decision enforce(Request request) {
        var decision = decide(request);
        if (!decision.allowed()) throw new PrivacyPolicyException(decision);
        return decision;
    }

    record Subject(
        String subjectId,
        String userId,
        boolean authenticated,
        String type
    ) {}

    record Resource(
        String resourceId,
        String ownerUserId,
        String domain,
        PrivacyScope scope,
        String relationshipSpaceId,
        boolean rawSensitive
    ) {}

    record Relationship(
        String relationshipSpaceId,
        Set<String> participantUserIds,
        boolean active,
        long version
    ) {}

    record Context(
        String purpose,
        String traceId,
        String policyVersion,
        String requiredConsentType,
        String requiredConsentPolicyVersion,
        PrivacyScope targetScope,
        Relationship relationship,
        boolean antiEnumeration
    ) {}

    record Request(
        Subject subject,
        Resource resource,
        PolicyAction action,
        Context context
    ) {}

    record Decision(
        boolean allowed,
        PolicyDecisionCode code,
        String reason,
        String policyVersion,
        String consentVersion,
        PrivacyScope scope,
        int httpStatus
    ) {}
}
