package com.momna.modules.auth.api;

import com.momna.modules.auth.application.AuthApplicationService;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.auth.domain.SessionCredentials;
import com.momna.modules.auth.infrastructure.AuthSessionEntity;
import com.momna.modules.auth.infrastructure.LoginIdentityEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthApplicationService auth;

    public AuthController(AuthApplicationService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/email/challenges")
    public ResponseEntity<ChallengeResponse> beginEmail(@Valid @RequestBody BeginEmailRequest request) {
        var result = auth.beginEmailSignIn(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new ChallengeResponse(result.challengeId(), result.expiresAt(), result.accepted()));
    }

    @PostMapping("/auth/email/complete")
    public SessionCredentialsResponse completeEmail(@Valid @RequestBody CompleteEmailRequest request) {
        return credentials(auth.completeEmailSignIn(
            request.challengeId(), request.email(), request.code(), request.deviceLabel()
        ));
    }

    @PostMapping("/auth/refresh")
    public SessionCredentialsResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return credentials(auth.refreshSession(request.refreshCredential()));
    }

    @GetMapping("/me/session")
    public SafeSession current(Authentication authentication) {
        var actor = actor(authentication);
        return safe(auth.current(actor), true);
    }

    @GetMapping("/me/sessions")
    public PagedSessions sessions(
        Authentication authentication,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        validatePage(limit, offset);
        var actor = actor(authentication);
        var all = auth.listSessions(actor);
        var items = all.stream().skip(offset).limit(limit).map(x -> safe(x, x.getSessionId().equals(actor.sessionId()))).toList();
        Integer nextOffset = offset + items.size() < all.size() ? offset + items.size() : null;
        return new PagedSessions(items, nextOffset);
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    public ResponseEntity<Void> revoke(Authentication authentication, @PathVariable String sessionId) {
        auth.revokeSession(actor(authentication), sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/sessions")
    public ResponseEntity<Void> revokeAll(Authentication authentication) {
        auth.revokeAllSessions(actor(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        auth.logout(actor(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/login-identities")
    public IdentityList identities(Authentication authentication) {
        return new IdentityList(
            auth.listIdentities(actor(authentication)).stream().map(this::safe).toList()
        );
    }

    private AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new com.momna.modules.auth.application.AuthException("AUTH_REQUIRED", "Authentication required");
        }
        return actor;
    }

    private SessionCredentialsResponse credentials(SessionCredentials value) {
        return new SessionCredentialsResponse(
            value.accessCredential(), value.refreshCredential(), safe(value.session(), true)
        );
    }

    private SafeSession safe(AuthSessionEntity value, boolean current) {
        return new SafeSession(
            value.getSessionId(), value.getCreatedAt(), value.getAuthenticatedAt(),
            value.getAccessExpiresAt(), value.getRefreshExpiresAt(), value.getStatus().name(),
            value.getDeviceLabel(), current
        );
    }

    private SafeLoginIdentity safe(LoginIdentityEntity value) {
        return new SafeLoginIdentity(
            value.getIdentityId(), value.getProvider().name(), value.getVerifiedEmail(), value.getCreatedAt()
        );
    }

    private void validatePage(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }
    }

    public record BeginEmailRequest(@NotBlank @Email String email) {}
    public record CompleteEmailRequest(
        @NotBlank String challengeId,
        @NotBlank @Email String email,
        @NotBlank String code,
        String deviceLabel
    ) {}
    public record RefreshRequest(@NotBlank String refreshCredential) {}
    public record ChallengeResponse(String challengeId, Instant expiresAt, boolean accepted) {}
    public record SessionCredentialsResponse(
        String accessCredential,
        String refreshCredential,
        SafeSession session
    ) {}
    public record SafeSession(
        String sessionId,
        Instant createdAt,
        Instant authenticatedAt,
        Instant accessExpiresAt,
        Instant refreshExpiresAt,
        String status,
        String deviceLabel,
        boolean current
    ) {}
    public record PagedSessions(List<SafeSession> items, Integer nextOffset) {}
    public record SafeLoginIdentity(
        String identityId,
        String provider,
        String verifiedEmail,
        Instant createdAt
    ) {}
    public record IdentityList(List<SafeLoginIdentity> items) {}
}
