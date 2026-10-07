package com.momna.modules.auth.application;

import com.momna.modules.auth.domain.*;
import com.momna.modules.auth.infrastructure.*;
import com.momna.shared.outbox.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService {
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final Duration EMAIL_CHALLENGE_TTL = Duration.ofMinutes(10);

    private final AuthAccountRepository accounts;
    private final LoginIdentityRepository identities;
    private final AuthSessionRepository sessions;
    private final EmailChallengeRepository challenges;
    private final AuthRefreshHistoryRepository refreshHistory;
    private final OutboxEventRepository outbox;
    private final CredentialHasher hasher;
    private final OpaqueCredentialGenerator credentials;
    private final ObjectProvider<EmailChallengeDelivery> emailDelivery;
    private final Clock clock = Clock.systemUTC();

    public AuthApplicationService(
        AuthAccountRepository accounts,
        LoginIdentityRepository identities,
        AuthSessionRepository sessions,
        EmailChallengeRepository challenges,
        AuthRefreshHistoryRepository refreshHistory,
        OutboxEventRepository outbox,
        CredentialHasher hasher,
        OpaqueCredentialGenerator credentials,
        ObjectProvider<EmailChallengeDelivery> emailDelivery
    ) {
        this.accounts = accounts;
        this.identities = identities;
        this.sessions = sessions;
        this.challenges = challenges;
        this.refreshHistory = refreshHistory;
        this.outbox = outbox;
        this.hasher = hasher;
        this.credentials = credentials;
        this.emailDelivery = emailDelivery;
    }

    @Transactional
    public ChallengeResponse beginEmailSignIn(String rawEmail) {
        var email = normalizeEmail(rawEmail);
        var delivery = emailDelivery.getIfAvailable();
        if (delivery == null) {
            throw new AuthException("CORE_API_UNAVAILABLE", "Email challenge delivery is not configured");
        }

        var now = clock.instant();
        var code = credentials.numericCode();
        var challenge = new EmailChallengeEntity(
            UUID.randomUUID().toString(),
            hasher.sha256(email),
            hasher.sha256(code),
            now,
            now.plus(EMAIL_CHALLENGE_TTL)
        );
        challenges.save(challenge);
        delivery.deliver(email, code);
        return new ChallengeResponse(challenge.getChallengeId(), challenge.getExpiresAt(), true);
    }

    @Transactional
    public SessionCredentials completeEmailSignIn(
        String challengeId, String rawEmail, String code, String deviceLabel
    ) {
        requireText(challengeId, "challengeId");
        requireText(code, "code");
        var email = normalizeEmail(rawEmail);
        var now = clock.instant();

        var challenge = challenges.findById(challengeId)
            .orElseThrow(() -> new AuthException("AUTH_INVALID", "Sign-in credential is invalid or expired"));

        if (!challenge.consume(hasher.sha256(email), hasher.sha256(code), now)) {
            throw new AuthException("AUTH_INVALID", "Sign-in credential is invalid or expired");
        }
        challenges.save(challenge);

        var identity = identities.findByProviderAndProviderSubject(AuthProvider.EMAIL, email).orElse(null);
        var account = identity == null ? createEmailAccount(email, now) : requireActive(identity.getUserId());
        return createSession(account.getUserId(), now, deviceLabel, UUID.randomUUID().toString());
    }

    @Transactional
    public SessionCredentials refreshSession(String refreshCredential) {
        requireText(refreshCredential, "refreshCredential");
        var now = clock.instant();
        var refreshHash = hasher.sha256(refreshCredential);

        var reused = refreshHistory.findById(refreshHash).orElse(null);
        if (reused != null) {
            for (var familySession : sessions.findByFamilyId(reused.getFamilyId())) {
                if (familySession.getStatus() == AuthSessionStatus.ACTIVE) {
                    familySession.revoke(now, null);
                }
            }
            publish("SessionRevoked", "unknown", now, Map.of(
                "familyId", reused.getFamilyId(),
                "reason", "refresh_reuse"
            ));
            throw new AuthException("SESSION_REVOKED", "Refresh credential reuse detected");
        }

        var current = sessions.findByRefreshHash(refreshHash)
            .orElseThrow(() -> new AuthException("AUTH_INVALID", "Refresh credential is invalid"));

        if (current.getStatus() != AuthSessionStatus.ACTIVE || !now.isBefore(current.getRefreshExpiresAt())) {
            throw new AuthException("AUTH_INVALID", "Refresh credential is invalid");
        }

        requireActive(current.getUserId());
        refreshHistory.save(new AuthRefreshHistoryEntity(refreshHash, current.getFamilyId(), now));

        var access = credentials.opaque();
        var refresh = credentials.opaque();
        var replacement = new AuthSessionEntity(
            UUID.randomUUID().toString(),
            current.getFamilyId(),
            current.getUserId(),
            hasher.sha256(access),
            hasher.sha256(refresh),
            now,
            current.getAuthenticatedAt(),
            now.plus(ACCESS_TTL),
            now.plus(REFRESH_TTL),
            AuthSessionStatus.ACTIVE,
            current.getDeviceLabel()
        );

        current.revoke(now, replacement.getSessionId());
        sessions.save(current);
        sessions.save(replacement);
        publish("SessionCreated", current.getUserId(), now, Map.of(
            "sessionId", replacement.getSessionId(),
            "reason", "refresh_rotation"
        ));
        return new SessionCredentials(access, refresh, replacement);
    }

    @Transactional(readOnly = true)
    public AuthenticatedActor authenticate(String accessCredential) {
        requireText(accessCredential, "accessCredential");
        var now = clock.instant();
        var session = sessions.findByAccessHash(hasher.sha256(accessCredential))
            .orElseThrow(() -> new AuthException("AUTH_INVALID", "Authentication credential is invalid"));
        if (session.getStatus() != AuthSessionStatus.ACTIVE) {
            throw new AuthException("SESSION_REVOKED", "Session revoked");
        }
        if (!now.isBefore(session.getAccessExpiresAt())) {
            throw new AuthException("AUTH_EXPIRED", "Authentication credential expired");
        }
        requireActive(session.getUserId());
        return new AuthenticatedActor(
            session.getUserId(), session.getSessionId(), session.getAuthenticatedAt(), session.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AuthSessionEntity current(AuthenticatedActor actor) {
        return ownSession(actor, actor.sessionId());
    }

    @Transactional(readOnly = true)
    public List<AuthSessionEntity> listSessions(AuthenticatedActor actor) {
        return sessions.findByUserIdOrderByCreatedAtDescSessionIdAsc(actor.userId());
    }

    @Transactional
    public void revokeSession(AuthenticatedActor actor, String sessionId) {
        var session = ownSession(actor, sessionId);
        if (session.getStatus() == AuthSessionStatus.ACTIVE) {
            var now = clock.instant();
            session.revoke(now, null);
            sessions.save(session);
            publish("SessionRevoked", actor.userId(), now, Map.of("sessionId", sessionId));
        }
    }

    @Transactional
    public void logout(AuthenticatedActor actor) {
        revokeSession(actor, actor.sessionId());
    }

    @Transactional
    public void revokeAllSessions(AuthenticatedActor actor) {
        requireRecent(actor);
        var now = clock.instant();
        for (var session : sessions.findByUserIdOrderByCreatedAtDescSessionIdAsc(actor.userId())) {
            if (session.getStatus() == AuthSessionStatus.ACTIVE) session.revoke(now, null);
        }
        publish("AllSessionsRevoked", actor.userId(), now, Map.of());
    }

    @Transactional(readOnly = true)
    public List<LoginIdentityEntity> listIdentities(AuthenticatedActor actor) {
        return identities.findByUserIdOrderByCreatedAtAscIdentityIdAsc(actor.userId());
    }

    private AuthAccountEntity createEmailAccount(String email, Instant now) {
        var account = new AuthAccountEntity(UUID.randomUUID().toString(), AuthAccountStatus.ACTIVE, now);
        accounts.save(account);
        identities.save(new LoginIdentityEntity(
            UUID.randomUUID().toString(), account.getUserId(), AuthProvider.EMAIL, email, email, now
        ));
        publish("AccountCreated", account.getUserId(), now, Map.of());
        publish("LoginIdentityLinked", account.getUserId(), now, Map.of("provider", "EMAIL"));
        return account;
    }

    private SessionCredentials createSession(String userId, Instant authenticatedAt, String deviceLabel, String familyId) {
        var now = clock.instant();
        var access = credentials.opaque();
        var refresh = credentials.opaque();
        var label = deviceLabel == null ? null : deviceLabel.trim();
        if (label != null && label.length() > 120) label = label.substring(0, 120);

        var session = new AuthSessionEntity(
            UUID.randomUUID().toString(),
            familyId,
            userId,
            hasher.sha256(access),
            hasher.sha256(refresh),
            now,
            authenticatedAt,
            now.plus(ACCESS_TTL),
            now.plus(REFRESH_TTL),
            AuthSessionStatus.ACTIVE,
            label
        );
        sessions.save(session);
        publish("SessionCreated", userId, now, Map.of("sessionId", session.getSessionId()));
        return new SessionCredentials(access, refresh, session);
    }

    private AuthAccountEntity requireActive(String userId) {
        var account = accounts.findById(userId)
            .orElseThrow(() -> new AuthException("AUTH_INVALID", "Account unavailable"));
        if (account.getStatus() != AuthAccountStatus.ACTIVE) {
            throw new AuthException("ACCOUNT_DISABLED", "Account unavailable");
        }
        return account;
    }

    private AuthSessionEntity ownSession(AuthenticatedActor actor, String sessionId) {
        return sessions.findById(sessionId)
            .filter(s -> s.getUserId().equals(actor.userId()))
            .orElseThrow(() -> new AuthException("AUTH_INVALID", "Session unavailable"));
    }

    private void requireRecent(AuthenticatedActor actor) {
        if (clock.instant().isAfter(actor.authenticatedAt().plus(Duration.ofMinutes(10)))) {
            throw new AuthException("REAUTH_REQUIRED", "Recent authentication is required");
        }
    }

    private String normalizeEmail(String value) {
        requireText(value, "email");
        var email = value.trim().toLowerCase(Locale.ROOT);
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        return email;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    private void publish(String type, String userId, Instant at, Map<String, Object> extra) {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("userId", userId);
        payload.putAll(extra);
        outbox.save(new OutboxEventEntity(type + "-" + UUID.randomUUID(), type, at, payload));
    }

    public record ChallengeResponse(String challengeId, Instant expiresAt, boolean accepted) {}
}
