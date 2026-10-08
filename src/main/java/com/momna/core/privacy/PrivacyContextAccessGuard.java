package com.momna.core.privacy;

import com.momna.core.context.application.ContextAccessGuard;
import org.springframework.stereotype.Component;

@Component
public class PrivacyContextAccessGuard implements ContextAccessGuard {
    private final PrivacyPolicyService privacy;

    public PrivacyContextAccessGuard(PrivacyPolicyService privacy) {
        this.privacy = privacy;
    }

    @Override
    public boolean allowed(
        String userId,
        String sensitivityScope,
        String purpose,
        boolean consentRequired
    ) {
        PrivacyScope scope;
        try {
            scope = PrivacyScope.valueOf(sensitivityScope);
        } catch (RuntimeException failure) {
            scope = PrivacyScope.GENERAL_PROFILE;
        }

        var requiredConsent = consentRequired ? "context:" + purpose : null;
        var decision = privacy.decide(new PrivacyPolicyService.Request(
            new PrivacyPolicyService.Subject(userId, userId, true, "USER"),
            new PrivacyPolicyService.Resource(
                "context:" + purpose,
                userId,
                "core.context",
                scope,
                null,
                scope != PrivacyScope.PUBLIC_CONTENT
            ),
            PolicyAction.READ,
            new PrivacyPolicyService.Context(
                purpose,
                "context-access",
                CanonicalPrivacyPolicyService.CURRENT_POLICY_VERSION,
                requiredConsent,
                null,
                scope,
                null,
                false
            )
        ));
        return decision.allowed();
    }
}
