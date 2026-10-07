package com.momna.modules.auth.application;

import com.momna.modules.auth.domain.AuthProvider;
import java.time.Instant;

public interface ProviderCredentialVerifier {
    AuthProvider provider();

    VerifiedProviderIdentity verify(ProviderCredential credential);

    record ProviderCredential(
        AuthProvider provider,
        String credential,
        String state,
        String nonce,
        String pkceVerifier
    ) {}

    record VerifiedProviderIdentity(
        AuthProvider provider,
        String providerSubject,
        String verifiedEmail,
        Instant authenticatedAt
    ) {}
}
