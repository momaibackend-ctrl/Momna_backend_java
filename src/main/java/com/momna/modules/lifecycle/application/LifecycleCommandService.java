package com.momna.modules.lifecycle.application;

import com.momna.modules.lifecycle.domain.LifecyclePeriod;
import com.momna.modules.lifecycle.infrastructure.*;
import com.momna.platform.audit.LegacyAuditBridge;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LifecycleCommandService {
    private static final double CONFIDENCE_CONFIRMATION_THRESHOLD = 0.8;

    private final LifecyclePeriodHistoryRepository history;
    private final LifecycleContextRepository contexts;
    private final LifecycleTransitionRepository transitions;
    private final LegacyAuditBridge audit;
    private final Clock clock = Clock.systemUTC();

    public LifecycleCommandService(
        LifecyclePeriodHistoryRepository history,
        LifecycleContextRepository contexts,
        LifecycleTransitionRepository transitions,
        LegacyAuditBridge audit
    ) {
        this.history = history;
        this.contexts = contexts;
        this.transitions = transitions;
        this.audit = audit;
    }

    @Transactional
    public LifecycleTransitionEntity proposeTransition(
        String userId,
        LifecyclePeriod toPeriod,
        String toSubstage,
        String reason,
        String ruleVersion,
        String source,
        double confidence,
        boolean selectedManually,
        String idempotencyKey,
        String expectedCurrentHistoryId
    ) {
        requireText(userId, "userId");
        requireText(reason, "reason");
        requireText(ruleVersion, "ruleVersion");
        requireText(source, "source");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                "confidence must be between 0 and 1"
            );
        }

        var now = clock.instant();
        var transitionId = idempotencyKey == null
            ? UUID.randomUUID().toString()
            : UUID.nameUUIDFromBytes(
                (userId + ":" + idempotencyKey)
                    .getBytes(StandardCharsets.UTF_8)
            ).toString();

        var existing = transitions.findByIdAndUserId(
            transitionId,
            userId
        ).orElse(null);
        if (existing != null) {
            if (
                existing.getToPeriod() != toPeriod
                    || !java.util.Objects.equals(
                        existing.getToSubstage(),
                        toSubstage
                    )
                    || !existing.getReason().equals(reason)
                    || !existing.getRuleVersion().equals(ruleVersion)
                    || !existing.getSource().equals(source)
                    || existing.getConfidence() != confidence
                    || existing.isSelectedManually() != selectedManually
            ) {
                throw new IllegalArgumentException(
                    "Idempotency key was reused with different transition data"
                );
            }
            return existing;
        }

        var current = history.findCurrent(userId, now).stream()
            .findFirst()
            .orElse(null);

        if (
            expectedCurrentHistoryId != null
                && !java.util.Objects.equals(
                    expectedCurrentHistoryId,
                    current == null ? null : current.getId()
                )
        ) {
            throw new IllegalStateException(
                "Lifecycle current-state version conflict"
            );
        }

        if (
            current != null
                && current.getPeriod() == toPeriod
                && java.util.Objects.equals(
                    current.getSubstage(),
                    toSubstage
                )
        ) {
            throw new IllegalArgumentException(
                "Transition must change period or substage"
            );
        }

        var disputed =
            confidence < CONFIDENCE_CONFIRMATION_THRESHOLD
                || (
                    current != null
                        && current.isSelectedManually()
                        && current.getPeriod() != toPeriod
                        && !selectedManually
                );

        return transitions.save(
            new LifecycleTransitionEntity(
                transitionId,
                userId,
                current == null ? null : current.getPeriod(),
                current == null ? null : current.getSubstage(),
                toPeriod,
                toSubstage,
                reason,
                disputed ? "DISPUTED" : "PROPOSED",
                ruleVersion,
                now,
                source,
                confidence,
                selectedManually
            )
        );
    }

    @Transactional
    public LifecycleTransitionEntity confirmTransition(
        String userId,
        String transitionId
    ) {
        requireText(userId, "userId");
        requireText(transitionId, "transitionId");

        var transition = transitions.findByIdAndUserId(
            transitionId,
            userId
        ).orElseThrow(() -> new IllegalArgumentException(
            "Lifecycle transition not found"
        ));

        if ("CONFIRMED".equals(transition.getConfirmationState())) {
            return transition;
        }
        if (
            !"PROPOSED".equals(transition.getConfirmationState())
                && !"DISPUTED".equals(
                    transition.getConfirmationState()
                )
        ) {
            throw new IllegalStateException(
                "Lifecycle transition cannot be confirmed"
            );
        }

        var now = clock.instant();
        history.findCurrent(userId, now).stream()
            .findFirst()
            .ifPresent(current -> {
                current.closeAt(now);
                history.save(current);
            });

        history.save(
            new LifecyclePeriodHistoryEntity(
                UUID.randomUUID().toString(),
                userId,
                transition.getToPeriod(),
                transition.getToSubstage(),
                now,
                null,
                transition.getSource(),
                transition.getConfidence(),
                transition.isSelectedManually()
            )
        );

        transition.confirm();
        transition = transitions.save(transition);

        audit.record(
            "lifecycle.transition.confirmed",
            userId,
            transitionId
        );
        return transition;
    }

    @Transactional
    public LifecycleContextEntity addContext(
        String userId,
        String contextType,
        Instant validFrom,
        Instant validTo,
        String source,
        double confidence
    ) {
        requireText(userId, "userId");
        requireText(contextType, "contextType");
        requireText(source, "source");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                "confidence must be between 0 and 1"
            );
        }
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException(
                "validTo must be after validFrom"
            );
        }

        var created = contexts.save(
            new LifecycleContextEntity(
                UUID.randomUUID().toString(),
                userId,
                contextType,
                validFrom,
                validTo,
                source,
                confidence
            )
        );
        audit.record(
            "lifecycle.context.added",
            userId,
            created.getId()
        );
        return created;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                field + " is required"
            );
        }
    }
}
