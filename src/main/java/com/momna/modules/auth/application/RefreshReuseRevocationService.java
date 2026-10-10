package com.momna.modules.auth.application;

import com.momna.modules.auth.domain.AuthSessionStatus;
import com.momna.modules.auth.infrastructure.AuthSessionRepository;
import com.momna.shared.outbox.OutboxEventEntity;
import com.momna.shared.outbox.OutboxEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Replay revocations must commit even though the HTTP request is rejected. */
@Service
public class RefreshReuseRevocationService {
    private final AuthSessionRepository sessions;
    private final OutboxEventRepository outbox;

    public RefreshReuseRevocationService(
        AuthSessionRepository sessions,
        OutboxEventRepository outbox
    ) {
        this.sessions = sessions;
        this.outbox = outbox;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamilyInNewTransaction(String familyId, Instant at) {
        for (var session : sessions.findByFamilyId(familyId)) {
            if (session.getStatus() == AuthSessionStatus.ACTIVE) {
                session.revoke(at, null);
                sessions.save(session);
            }
        }
        outbox.save(new OutboxEventEntity(
            "SessionRevoked-" + UUID.randomUUID(), "SessionRevoked",
            at, Map.of("familyId", familyId, "reason", "refresh_reuse")
        ));
    }
}
