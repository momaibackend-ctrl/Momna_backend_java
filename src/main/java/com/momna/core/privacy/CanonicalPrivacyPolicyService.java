package com.momna.core.privacy;

import com.momna.modules.profile.domain.ConsentState;
import com.momna.modules.profile.infrastructure.ConsentRecordRepository;
import com.momna.platform.audit.AuditService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanonicalPrivacyPolicyService implements PrivacyPolicyService {
    public static final String CURRENT_POLICY_VERSION = "privacy-v1";

    private final ConsentRecordRepository consents;
    private final AuditService audit;

    public CanonicalPrivacyPolicyService(
        ConsentRecordRepository consents,
        AuditService audit
    ) {
        this.consents = consents;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public Decision decide(Request request) {
        var decision = evaluate(request);
        recordAudit(request, decision);
        return decision;
    }

    private Decision evaluate(Request request) {
        if (request.subject() == null || !request.subject().authenticated()) {
            return deny(request, PolicyDecisionCode.UNAUTHENTICATED, null);
        }
        if (request.context() == null || !CURRENT_POLICY_VERSION.equals(request.context().policyVersion())) {
            return deny(request, PolicyDecisionCode.POLICY_VERSION_MISMATCH, null);
        }
        if (request.resource() == null || request.resource().scope() == null) {
            return deny(request, PolicyDecisionCode.VALIDATION_ERROR, null);
        }
        if (request.resource().scope() == PrivacyScope.COUPLE_SHARED
            && blank(request.resource().relationshipSpaceId())) {
            return deny(request, PolicyDecisionCode.VALIDATION_ERROR, null);
        }

        var actor = effectiveUserId(request.subject());
        var owner = request.resource().ownerUserId();

        switch (request.resource().scope()) {
            case PUBLIC_CONTENT -> {
                if (!Set.of(PolicyAction.READ, PolicyAction.AI_USE, PolicyAction.ANALYTICS_USE)
                    .contains(request.action())
                    && (owner == null || (!Objects.equals(actor, owner)
                        && !"ADMIN".equalsIgnoreCase(request.subject().type())))) {
                    return deny(request, PolicyDecisionCode.OWNER_REQUIRED, null);
                }
            }
            case COUPLE_SHARED -> {
                var relationship = relationshipDenial(request, actor, owner != null);
                if (relationship != null) return relationship;
            }
            default -> {
                if (owner == null || !Objects.equals(actor, owner)) {
                    var rel = request.context().relationship();
                    var partner = actor != null && owner != null && rel != null && rel.active()
                        && rel.participantUserIds().contains(actor)
                        && rel.participantUserIds().contains(owner);
                    return deny(
                        request,
                        partner ? PolicyDecisionCode.PARTNER_PRIVATE_DENIED : PolicyDecisionCode.OWNER_REQUIRED,
                        null
                    );
                }
            }
        }

        if (request.action() == PolicyAction.SHARE) {
            if (request.resource().scope() != PrivacyScope.COUPLE_SHARED
                && request.context().targetScope() != PrivacyScope.COUPLE_SHARED) {
                return deny(request, PolicyDecisionCode.SCOPE_NOT_ALLOWED, null);
            }
            var relationship = relationshipDenial(request, actor, true);
            if (relationship != null) return relationship;
        }

        var crossBoundary = Set.of(
            PolicyAction.SHARE,
            PolicyAction.AI_USE,
            PolicyAction.ANALYTICS_USE
        ).contains(request.action());

        if (request.resource().rawSensitive()
            && crossBoundary
            && blank(request.context().requiredConsentType())) {
            return deny(request, PolicyDecisionCode.RAW_SENSITIVE_CROSS_BOUNDARY_DENIED, null);
        }

        String consentVersion = null;
        if (crossBoundary && request.resource().scope() != PrivacyScope.PUBLIC_CONTENT) {
            var consentType = request.context().requiredConsentType();
            if (blank(consentType)) return deny(request, PolicyDecisionCode.CONSENT_REQUIRED, null);

            var consentUser = owner != null ? owner : actor;
            if (consentUser == null) return deny(request, PolicyDecisionCode.CONSENT_REQUIRED, null);

            var latest = consents.findByUserIdOrderByRecordedAtAscIdAsc(consentUser).stream()
                .filter(x -> x.getConsentType().equals(consentType))
                .max(Comparator.comparing(com.momna.modules.profile.infrastructure.ConsentRecordEntity::getRecordedAt)
                    .thenComparing(com.momna.modules.profile.infrastructure.ConsentRecordEntity::getId))
                .orElse(null);

            if (latest == null) return deny(request, PolicyDecisionCode.CONSENT_REQUIRED, null);

            if (!blank(request.context().requiredConsentPolicyVersion())
                && !request.context().requiredConsentPolicyVersion().equals(latest.getPolicyVersion())) {
                return deny(request, PolicyDecisionCode.CONSENT_VERSION_MISMATCH, latest.getPolicyVersion());
            }
            if (latest.getState() == ConsentState.WITHDRAWN) {
                return deny(request, PolicyDecisionCode.CONSENT_WITHDRAWN, latest.getPolicyVersion());
            }
            consentVersion = latest.getPolicyVersion();
        }

        return allow(request, consentVersion);
    }

    private Decision relationshipDenial(Request request, String actor, boolean requireOwner) {
        var rel = request.context().relationship();
        if (rel == null) return deny(request, PolicyDecisionCode.RELATIONSHIP_REQUIRED, null);
        if (!rel.active()) return deny(request, PolicyDecisionCode.RELATIONSHIP_INACTIVE, null);
        if (!blank(request.resource().relationshipSpaceId())
            && !request.resource().relationshipSpaceId().equals(rel.relationshipSpaceId())) {
            return deny(request, PolicyDecisionCode.RELATIONSHIP_REQUIRED, null);
        }
        if (actor == null || !rel.participantUserIds().contains(actor) || rel.participantUserIds().size() < 2) {
            return deny(request, PolicyDecisionCode.RELATIONSHIP_REQUIRED, null);
        }
        if (requireOwner && request.resource().ownerUserId() != null
            && !rel.participantUserIds().contains(request.resource().ownerUserId())) {
            return deny(request, PolicyDecisionCode.RELATIONSHIP_REQUIRED, null);
        }
        return null;
    }

    private String effectiveUserId(Subject subject) {
        if ("USER".equalsIgnoreCase(subject.type())) {
            return blank(subject.userId()) ? subject.subjectId() : subject.userId();
        }
        return subject.userId();
    }

    private Decision allow(Request request, String consentVersion) {
        return decision(request, true, PolicyDecisionCode.ALLOW, consentVersion);
    }

    private Decision deny(Request request, PolicyDecisionCode code, String consentVersion) {
        return decision(request, false, code, consentVersion);
    }

    private Decision decision(
        Request request,
        boolean allowed,
        PolicyDecisionCode code,
        String consentVersion
    ) {
        int status;
        if (allowed) status = 200;
        else if (code == PolicyDecisionCode.UNAUTHENTICATED) status = 401;
        else if (code == PolicyDecisionCode.VALIDATION_ERROR
            || code == PolicyDecisionCode.POLICY_VERSION_MISMATCH) status = 400;
        else if (request.context() != null && request.context().antiEnumeration()) status = 404;
        else status = 403;

        return new Decision(
            allowed,
            code,
            code.name().toLowerCase(Locale.ROOT),
            CURRENT_POLICY_VERSION,
            consentVersion,
            request.resource() == null ? null : request.resource().scope(),
            status
        );
    }

    private void recordAudit(Request request, Decision decision) {
        try {
            audit.record(new AuditService.Command(
                "LEGACY_TECHNICAL",
                technical(request.context() == null ? null : request.context().traceId(), "privacy-request"),
                technical(request.context() == null ? null : request.context().traceId(), "privacy-trace"),
                technical(request.subject() == null ? null : request.subject().subjectId(), "privacy-actor"),
                technical(request.resource() == null ? null : request.resource().resourceId(), "privacy-resource"),
                "core.privacy",
                null,
                null,
                "privacy." + request.action().name().toLowerCase(Locale.ROOT),
                decision.code().name(),
                CURRENT_POLICY_VERSION,
                null,
                null,
                null,
                Map.of("privacyPolicyVersion", CURRENT_POLICY_VERSION),
                Map.of(
                    "domain", request.resource() == null ? "unknown" : request.resource().domain(),
                    "scope", request.resource() == null || request.resource().scope() == null
                        ? "unknown" : request.resource().scope().name()
                )
            ));
        } catch (RuntimeException ignored) {
            // Privacy decisions must not become unavailable because telemetry failed.
        }
    }

    private String technical(String value, String fallback) {
        if (blank(value)) return fallback;
        var sanitized = value.replaceAll("[^A-Za-z0-9_.:/-]", "_");
        return sanitized.substring(0, Math.min(160, sanitized.length()));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
