package com.momna.platform.security;

import com.momna.modules.auth.application.AuthApplicationService;
import org.springframework.stereotype.Component;

@Component
public class AuthSecurityAdapter implements SecurityContract {
    private final AuthApplicationService auth;

    public AuthSecurityAdapter(AuthApplicationService auth) {
        this.auth = auth;
    }

    @Override
    public AuthenticatedPrincipal validateCredential(String credential) {
        var actor = auth.authenticate(credential);
        return new AuthenticatedPrincipal(actor.userId());
    }
}
