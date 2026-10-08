package com.momna.platform.security;

public interface SecurityContract {
    AuthenticatedPrincipal validateCredential(String credential);

    record AuthenticatedPrincipal(String userId) {
        public AuthenticatedPrincipal {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("Authenticated user id is required");
            }
        }
    }
}
