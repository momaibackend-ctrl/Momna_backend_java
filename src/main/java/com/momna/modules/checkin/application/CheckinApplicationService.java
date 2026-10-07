package com.momna.modules.checkin.application;

import static com.momna.modules.checkin.application.CheckinViews.*;

import com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog;
import com.momna.modules.checkin.domain.*;
import com.momna.modules.checkin.infrastructure.*;
import com.momna.modules.checkin.scoring.WeightedCheckinScoringEngine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinApplicationService {
    private final CheckinSessionRepository sessions;
    private final CheckinAnswerRepository answers;
    private final CheckinIdempotencyRepository idempotency;
    private final WeightedCheckinDefinitionCatalog definitions;
    private final WeightedCheckinScoringEngine scoring;
    private final CheckinDayContextService contexts;
    private final Clock clock = Clock.systemUTC();

    public CheckinApplicationService(
        CheckinSessionRepository sessions,
        CheckinAnswerRepository answers,
        CheckinIdempotencyRepository idempotency,
        WeightedCheckinDefinitionCatalog definitions,
        WeightedCheckinScoringEngine scoring,
        CheckinDayContextService contexts
    ) {
        this.sessions = sessions;
        this.answers = answers;
        this.idempotency = idempotency;
        this.definitions = definitions;
        this.scoring = scoring;
        this.contexts = contexts;
    }

    @Transactional(readOnly = true)
    public TodayView today(String userId) {
        var now = clock.instant();
        var day = contexts.resolve(userId, now);
        return new TodayView(
            day.localDate().toString(),
            day.timezone().getId(),
            day.period().name(),
            day.substage(),
            List.of(
                phaseSummary(userId, day, CheckinPhase.MORNING, now),
                phaseSummary(userId, day, CheckinPhase.EVENING, now)
            )
        );
    }

    @Transactional
    public StartResult startOrResume(String userId, CheckinPhase phase, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        var now = clock.instant();
        var day = contexts.resolve(userId, now);

        var existing = sessions.findByUserIdAndPhaseAndLocalDateAndPeriodAtTime(
            userId, phase, day.localDate(), day.period().name()
        ).orElse(null);
        if (existing != null) return new StartResult(view(existing, now), false);

        var window = day.window(phase);
        var windowState = window.stateAt(now);
        if (!"OPEN".equals(windowState)) {
            throw new CheckinException(
                "CLOSED".equals(windowState) ? "CHECKIN_WINDOW_CLOSED" : "CHECKIN_WINDOW_NOT_OPEN",
                "Check-in window is not open"
            );
        }

        var fingerprint = sha("start|" + phase + "|" + day.localDate() + "|" + day.period());
        var replay = replay(userId, "start", idempotencyKey, fingerprint);
        if (replay != null) return new StartResult(view(replay, now), false);

        var definition = definitions.latest(day.period(), phase);
        var candidate = new CheckinSessionEntity(
            UUID.randomUUID().toString(),
            userId,
            phase,
            day.localDate(),
            day.period().name(),
            day.substage(),
            day.timezone().getId(),
            definition.definitionVersion(),
            definition.poolVersion(),
            definition.items().stream().map(WeightedCheckinDefinitionCatalog.Item::itemCode).toList(),
            window.opensAt(),
            window.closesAt(),
            now
        );

        try {
            candidate = sessions.saveAndFlush(candidate);
        } catch (RuntimeException conflict) {
            var winner = sessions.findByUserIdAndPhaseAndLocalDateAndPeriodAtTime(
                userId, phase, day.localDate(), day.period().name()
            ).orElseThrow(() -> conflict);
            return new StartResult(view(winner, now), false);
        }

        remember(userId, "start", idempotencyKey, fingerprint, candidate);
        return new StartResult(view(candidate, now), true);
    }

    @Transactional
    public SessionView session(String userId, String sessionId) {
        var current = owned(userId, sessionId);
        var now = clock.instant();
        if (isMutable(current) && !now.isBefore(current.getWindowClosesAt()) && !current.isSafetyBlocking()) {
            current.autoFinalize(now);
            current = sessions.saveAndFlush(current);
        }
        return view(current, now);
    }

    @Transactional
    public SessionView patch(
        String userId,
        String sessionId,
        long expectedRevision,
        List<AnswerChange> changes,
        String idempotencyKey
    ) {
        requireIdempotencyKey(idempotencyKey);
        if (changes == null || changes.isEmpty()) {
            throw new IllegalArgumentException("answerChanges must not be empty");
        }
        if (changes.stream().map(AnswerChange::itemCode).distinct().count() != changes.size()) {
            throw new IllegalArgumentException("Duplicate itemCode in answerChanges");
        }

        var payload = changes.stream()
            .sorted(Comparator.comparing(AnswerChange::itemCode))
            .map(x -> x.itemCode() + "=" + x.value())
            .toList().toString();
        var fingerprint = sha("patch|" + sessionId + "|" + expectedRevision + "|" + payload);
        var replay = replay(userId, "patch", idempotencyKey, fingerprint);
        if (replay != null) return view(replay, clock.instant());

        var current = owned(userId, sessionId);
        var now = clock.instant();
        requireMutable(current, now);
        if (current.getRevision() != expectedRevision) {
            throw new CheckinException("STALE_REVISION", "Check-in revision conflict");
        }

        var applicable = new HashSet<>(current.getApplicableItemCodes());
        for (var change : changes) {
            if (change.itemCode() == null || change.itemCode().isBlank()) {
                throw new IllegalArgumentException("itemCode is required");
            }
            if (!applicable.contains(change.itemCode())) {
                throw new CheckinException("CHECKIN_ITEM_NOT_APPLICABLE", "Check-in item is not applicable");
            }
            if (change.value() != null && (change.value() < 0 || change.value() > 2)) {
                throw new CheckinException("CHECKIN_VALUE_INVALID", "Check-in value must be 0, 1, 2 or null");
            }

            var id = new CheckinAnswerId(sessionId, change.itemCode());
            if (change.value() == null) {
                answers.deleteById(id);
            } else {
                var answer = answers.findById(id).orElse(null);
                if (answer == null) {
                    answer = new CheckinAnswerEntity(sessionId, change.itemCode(), change.value(), now);
                } else {
                    answer.changeTo(change.value(), now);
                }
                answers.save(answer);
            }
        }

        var answerMap = answerMap(sessionId);
        current.updateCompletion(answerMap.size(), now);
        scoring.score(
            CheckinDefinitionPeriod.valueOf(current.getPeriodAtTime()),
            current.getPhase(),
            current.getDefinitionVersion(),
            applicable,
            answerMap,
            Map.of()
        );

        try {
            current = sessions.saveAndFlush(current);
        } catch (OptimisticLockingFailureException conflict) {
            throw new CheckinException("STALE_REVISION", "Check-in revision conflict");
        }

        remember(userId, "patch", idempotencyKey, fingerprint, current);
        return view(current, now);
    }

    @Transactional
    public FinalizeResult submit(
        String userId,
        String sessionId,
        long expectedRevision,
        String idempotencyKey
    ) {
        requireIdempotencyKey(idempotencyKey);
        var fingerprint = sha("submit|" + sessionId + "|" + expectedRevision);
        var replay = replay(userId, "submit", idempotencyKey, fingerprint);
        if (replay != null) {
            return new FinalizeResult(view(replay, clock.instant()), downstreamNone());
        }

        var current = owned(userId, sessionId);
        var now = clock.instant();
        requireMutable(current, now);
        if (current.getRevision() != expectedRevision) {
            throw new CheckinException("STALE_REVISION", "Check-in revision conflict");
        }
        if (current.isSafetyBlocking()) {
            throw new CheckinException("SAFETY_CLARIFICATION_REQUIRED", "Safety clarification is required");
        }

        current.finalizeByUser(now);
        current = sessions.saveAndFlush(current);
        remember(userId, "submit", idempotencyKey, fingerprint, current);
        return new FinalizeResult(view(current, now), downstreamNone());
    }

    private PhaseSummary phaseSummary(
        String userId,
        CheckinDayContextService.DayContext day,
        CheckinPhase phase,
        Instant now
    ) {
        var window = day.window(phase);
        var stored = sessions.findByUserIdAndPhaseAndLocalDateAndPeriodAtTime(
            userId, phase, day.localDate(), day.period().name()
        ).orElse(null);

        var action = stored == null
            ? ("OPEN".equals(window.stateAt(now)) ? "START" : "NONE")
            : (!isMutable(stored) || stored.getConsumedAt() != null ? "VIEW_FINAL_STATE" : "RESUME");

        return new PhaseSummary(
            phase.name(),
            window.stateAt(now),
            window.opensAt(),
            window.closesAt(),
            stored == null ? null : stored.getSessionId(),
            stored == null ? null : stored.getStatus().name(),
            stored == null ? null : stored.getConsumedAt(),
            stored == null ? null : stored.getConsumedByRef(),
            action
        );
    }

    private SessionView view(CheckinSessionEntity session, Instant now) {
        var period = CheckinDefinitionPeriod.valueOf(session.getPeriodAtTime());
        var definition = definitions.get(period, session.getPhase(), session.getDefinitionVersion());
        var answerList = answers.findBySessionIdOrderByItemCodeAsc(session.getSessionId()).stream()
            .map(x -> new AnswerView(x.getItemCode(), x.getValue()))
            .toList();

        var allowed = new LinkedHashSet<String>();
        if (session.getConsumedAt() != null || !isMutable(session)) {
            allowed.add("OPEN_MY_DAY");
        } else if (session.isSafetyBlocking()) {
            allowed.add("ANSWER_SAFETY");
        } else if (now.isBefore(session.getWindowClosesAt())) {
            allowed.add("EDIT_ANSWERS");
            allowed.add("SUBMIT");
        }

        return new SessionView(
            session.getSessionId(),
            session.getPhase().name(),
            session.getLocalDate().toString(),
            session.getTimezoneAtSession(),
            session.getPeriodAtTime(),
            session.getLifecycleSubstageAtTime(),
            session.getStatus().name(),
            session.getRevision(),
            String.valueOf(session.getDefinitionVersion()),
            session.getRulesVersion(),
            definition.items().stream().map(item -> new ItemView(
                item.itemCode(),
                item.displayOrder(),
                item.group(),
                "checkin.item." + item.itemCode().toLowerCase(Locale.ROOT),
                true,
                Set.of(0, 1, 2)
            )).toList(),
            answerList,
            session.getCompletionRatio().doubleValue(),
            new WindowView(
                now.isBefore(session.getWindowOpensAt()) ? "NOT_OPEN_YET"
                    : !now.isBefore(session.getWindowClosesAt()) ? "CLOSED" : "OPEN",
                session.getWindowOpensAt(),
                session.getWindowClosesAt()
            ),
            new SafetyView(session.getSafetyState().name(), session.isSafetyBlocking()),
            session.getFinalizedAt(),
            session.getFinalizationReason() == null ? null : session.getFinalizationReason().name(),
            session.getConsumedAt(),
            session.getConsumedByRef(),
            Set.copyOf(allowed)
        );
    }

    private CheckinSessionEntity owned(String userId, String sessionId) {
        return sessions.findBySessionIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new CheckinException("CHECKIN_SESSION_NOT_FOUND", "Check-in session not found"));
    }

    private void requireMutable(CheckinSessionEntity session, Instant now) {
        if (!isMutable(session)) {
            throw new CheckinException("CHECKIN_SESSION_FINALIZED", "Check-in session is finalized");
        }
        if (session.getConsumedAt() != null) {
            throw new CheckinException("CHECKIN_SESSION_CONSUMED", "Check-in session is already consumed");
        }
        if (now.isBefore(session.getWindowOpensAt())) {
            throw new CheckinException("CHECKIN_WINDOW_NOT_OPEN", "Check-in window is not open");
        }
        if (!now.isBefore(session.getWindowClosesAt())) {
            throw new CheckinException("CHECKIN_WINDOW_CLOSED", "Check-in window is closed");
        }
    }

    private boolean isMutable(CheckinSessionEntity session) {
        return session.getStatus() == CheckinSessionStatus.DRAFT
            || session.getStatus() == CheckinSessionStatus.PARTIAL;
    }

    private Map<String, Integer> answerMap(String sessionId) {
        var map = new LinkedHashMap<String, Integer>();
        for (var answer : answers.findBySessionIdOrderByItemCodeAsc(sessionId)) {
            map.put(answer.getItemCode(), answer.getValue());
        }
        return Map.copyOf(map);
    }

    private CheckinSessionEntity replay(
        String userId,
        String operation,
        String idempotencyKey,
        String fingerprint
    ) {
        var previous = idempotency.findById(
            new CheckinIdempotencyId(userId, operation, idempotencyKey)
        ).orElse(null);
        if (previous == null) return null;
        if (!previous.getFingerprintSha256().equals(fingerprint)) {
            throw new CheckinException("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with another request");
        }
        return owned(userId, previous.getResultSessionId());
    }

    private void remember(
        String userId,
        String operation,
        String idempotencyKey,
        String fingerprint,
        CheckinSessionEntity session
    ) {
        var id = new CheckinIdempotencyId(userId, operation, idempotencyKey);
        if (idempotency.existsById(id)) return;
        idempotency.save(new CheckinIdempotencyEntity(
            userId,
            operation,
            idempotencyKey,
            fingerprint,
            session.getSessionId(),
            session.getRevision(),
            clock.instant()
        ));
    }

    private void requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 160) {
            throw new IllegalArgumentException("A valid Idempotency-Key is required");
        }
    }

    private String sha(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            var out = new StringBuilder(64);
            for (var item : bytes) out.append("%02x".formatted(item & 0xff));
            return out.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private DownstreamView downstreamNone() {
        return new DownstreamView("NOT_REQUESTED", null, "NONE");
    }
}
