package com.momna.modules.checkin.application;

import static com.momna.modules.checkin.application.CheckinViews.*;

import com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog;
import com.momna.modules.checkin.domain.*;
import com.momna.modules.checkin.infrastructure.*;
import com.momna.modules.checkin.scoring.WeightedCheckinScoringEngine;
import com.momna.modules.checkin.safety.CheckinMedicalSafety;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import com.momna.modules.myday.application.MyDayMaterializationPort;
import com.momna.modules.profile.application.ProfileService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinApplicationService {
    private final CheckinSessionRepository sessions;
    private final CheckinAnswerRepository answers;
    private final CheckinIdempotencyRepository idempotency;
    private final CheckinAdjustmentSignalRepository adjustmentSignals;
    private final WeightedCheckinDefinitionCatalog definitions;
    private final WeightedCheckinScoringEngine scoring;
    private final CheckinMedicalSafety safety;
    private final CheckinDayContextService contexts;
    private final ObjectProvider<MyDayMaterializationPort> myDay;
    private final ProfileService profiles;
    private final Clock clock = Clock.systemUTC();

    public CheckinApplicationService(
        CheckinSessionRepository sessions,
        CheckinAnswerRepository answers,
        CheckinIdempotencyRepository idempotency,
        CheckinAdjustmentSignalRepository adjustmentSignals,
        WeightedCheckinDefinitionCatalog definitions,
        WeightedCheckinScoringEngine scoring,
        CheckinDayContextService contexts,
        CheckinMedicalSafety safety,
        ObjectProvider<MyDayMaterializationPort> myDay,
        ProfileService profiles
    ) {
        this.myDay = myDay;
        this.profiles = profiles;
        this.safety = safety;
        this.sessions = sessions;
        this.answers = answers;
        this.idempotency = idempotency;
        this.adjustmentSignals = adjustmentSignals;
        this.definitions = definitions;
        this.scoring = scoring;
        this.contexts = contexts;
    }

    @Transactional
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
            ),
            CheckinTexts.subtitle(preferredLanguage(userId))
        );
    }

    @Transactional
    public StartResult startOrResume(String userId, CheckinPhase phase, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        var now = clock.instant();
        var day = contexts.resolve(userId, now);

        var fingerprint = sha("start|" + phase + "|" + day.localDate() + "|" + day.period());
        var replay = replay(userId, "start", idempotencyKey, fingerprint);
        if (replay != null) {
            if (isMutable(replay) && !now.isBefore(replay.getWindowClosesAt())
                && !replay.isSafetyBlocking()) {
                replay.autoFinalize(now);
                replay = sessions.saveAndFlush(replay);
            }
            return new StartResult(view(replay, now), false);
        }

        var existing = sessions.findByUserIdAndPhaseAndLocalDateAndPeriodAtTime(
            userId, phase, day.localDate(), day.period().name()
        ).orElse(null);
        if (existing != null) {
            if (isMutable(existing) && !now.isBefore(existing.getWindowClosesAt())
                && !existing.isSafetyBlocking()) {
                existing.autoFinalize(now);
                existing = sessions.saveAndFlush(existing);
            }
            if (isMutable(existing)) requireMorningOpenForPlan(userId, phase, day.localDate());
            return new StartResult(view(existing, now), false);
        }
        requireMorningOpenForPlan(userId, phase, day.localDate());

        var window = day.window(phase);
        var windowState = window.stateAt(now);
        if (!"OPEN".equals(windowState)) {
            throw new CheckinException(
                "CLOSED".equals(windowState) ? "CHECKIN_WINDOW_CLOSED" : "CHECKIN_WINDOW_NOT_OPEN",
                "Check-in window is not open"
            );
        }

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
        // Answers stay editable while a safety question is open. Lowering the answer that opened it closes it
        // without a YES (MOMNA-1145 L13); any other open question keeps the lock until it is answered.

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
        var score = scoring.score(
            CheckinDefinitionPeriod.valueOf(current.getPeriodAtTime()),
            current.getPhase(),
            current.getDefinitionVersion(),
            applicable,
            answerMap,
            Map.of()
        );
        persistAdjustmentSignals(current, score, now);
        safety.onPatch(current, changes, answerMap, score, now);

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
        requireMorningOpenForPlan(userId, current.getPhase(), current.getLocalDate());

        current.finalizeByUser(now);
        current = sessions.saveAndFlush(current);
        remember(userId, "submit", idempotencyKey, fingerprint, current);
        return new FinalizeResult(view(current, now), downstreamNone());
    }

    /** The safety actions on a route the session opened (MOMNA-1145): one fact, a correction, QM, BP1, skip. */
    public enum SafetyAction { ANSWER, REVISE, CHECKLIST, BLOOD_PRESSURE, SKIP }

    /** The request of one safety action; fields that an action does not use are null. */
    public record SafetyRequest(
        SafetyAction action,
        String routeId,
        String factCode,
        String answer,
        String mode,
        List<String> selectedFactCodes,
        Integer systolic,
        Integer diastolic,
        Boolean unknown
    ) {
        String fingerprint() {
            var selected = selectedFactCodes == null ? List.<String>of()
                : selectedFactCodes.stream().sorted().toList();
            return action + "|" + routeId + "|" + factCode + "|" + answer + "|" + mode + "|" + selected
                + "|" + systolic + "|" + diastolic + "|" + Boolean.TRUE.equals(unknown);
        }
    }

    @Transactional
    public SessionView safety(
        String userId, String sessionId, long expectedRevision, SafetyRequest request, String idempotencyKey
    ) {
        requireIdempotencyKey(idempotencyKey);
        if (request == null || request.action() == null || request.routeId() == null || request.routeId().isBlank()) {
            throw new IllegalArgumentException("A safety action and route are required");
        }
        var operation = "safety-" + request.action().name().toLowerCase(Locale.ROOT);
        var fingerprint = sha(operation + "|" + sessionId + "|" + expectedRevision + "|" + request.fingerprint());
        var replay = replay(userId, operation, idempotencyKey, fingerprint);
        if (replay != null) return view(replay, clock.instant());

        var current = owned(userId, sessionId);
        if (current.getRevision() != expectedRevision) {
            throw new CheckinException("STALE_REVISION", "Check-in revision conflict");
        }
        if (!isMutable(current) || current.getConsumedAt() != null) {
            throw new CheckinException("CHECKIN_SESSION_FINALIZED", "Session is immutable");
        }
        var now = clock.instant();
        switch (request.action()) {
            case ANSWER -> safety.answer(current, request.routeId(), request.factCode(), request.answer(), now);
            case REVISE -> safety.revise(current, request.routeId(), request.factCode(), request.answer(), now);
            case CHECKLIST -> safety.checklist(current, request.routeId(), request.mode(),
                request.selectedFactCodes(), now);
            case BLOOD_PRESSURE -> safety.bloodPressure(current, request.routeId(), request.systolic(),
                request.diastolic(), Boolean.TRUE.equals(request.unknown()), now);
            case SKIP -> safety.skip(current, request.routeId(), now);
        }
        if (!current.isSafetyBlocking() && !now.isBefore(current.getWindowClosesAt())) {
            current.autoFinalize(now);
        }
        try {
            current = sessions.saveAndFlush(current);
        } catch (OptimisticLockingFailureException conflict) {
            throw new CheckinException("STALE_REVISION", "Check-in revision conflict");
        }
        remember(userId, operation, idempotencyKey, fingerprint, current);
        return view(current, now);
    }

    /**
     * One generation per day (MOMNA-989, 23.11.2026): once today's plan exists, the morning Check-in is
     * closed until the evening, so its answers can never be lost silently. A draft left at that moment
     * still auto-finalises when its window closes, but it does not join the existing My Day.
     */
    private void requireMorningOpenForPlan(String userId, CheckinPhase phase, LocalDate localDate) {
        if (phase == CheckinPhase.MORNING && planExists(userId, localDate)) {
            throw new CheckinException("CHECKIN_MORNING_PLAN_ALREADY_SET", "Today's plan is already set");
        }
    }

    private boolean planExists(String userId, LocalDate localDate) {
        var port = myDay.getIfAvailable();
        return port != null && port.findMyDayId(userId, localDate).isPresent();
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
        if (stored != null && isMutable(stored)
            && !now.isBefore(stored.getWindowClosesAt())
            && !stored.isSafetyBlocking()) {
            stored.autoFinalize(now);
            stored = sessions.saveAndFlush(stored);
        }

        var action = stored == null
            ? ("OPEN".equals(window.stateAt(now)) ? "START" : "NONE")
            : (!isMutable(stored) || stored.getConsumedAt() != null ? "VIEW_FINAL_STATE" : "RESUME");
        String blockedReason = null;
        String blockedText = null;
        if (phase == CheckinPhase.MORNING && (stored == null || isMutable(stored))
            && planExists(userId, day.localDate())) {
            action = "NONE";
            blockedReason = CheckinTexts.MORNING_PLAN_ALREADY_SET;
            blockedText = CheckinTexts.morningPlanAlreadySet(preferredLanguage(userId));
        }
        CheckinTexts.PhaseCard card = null;
        if (blockedReason == null) {
            if ("START".equals(action) || "RESUME".equals(action)) {
                card = CheckinTexts.invite(phase, preferredLanguage(userId));
            } else if ("VIEW_FINAL_STATE".equals(action)) {
                card = CheckinTexts.completed(phase, preferredLanguage(userId));
            }
        }

        return new PhaseSummary(
            phase.name(),
            window.stateAt(now),
            window.opensAt(),
            window.closesAt(),
            stored == null ? null : stored.getSessionId(),
            stored == null ? null : stored.getStatus().name(),
            stored == null ? null : stored.getConsumedAt(),
            stored == null ? null : stored.getConsumedByRef(),
            action,
            blockedReason,
            blockedText,
            card
        );
    }

    private String preferredLanguage(String userId) {
        var profile = profiles.get(userId);
        return profile == null ? null : profile.getPreferredLanguage();
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
            if (now.isBefore(session.getWindowOpensAt())) {
                // Nothing is actionable before the window opens.
            } else if (now.isBefore(session.getWindowClosesAt())) {
                allowed.add("EDIT_ANSWERS");
                allowed.add("ANSWER_SAFETY");
            } else {
                allowed.add("ANSWER_SAFETY");
            }
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
            new SafetyView(
                session.getSafetyState().name(),
                session.getSafetyDecisionVersion(),
                session.getSafetyRouteCode(),
                session.getSafetySeverity(),
                session.isSafetyBlocking(),
                session.getSafetyClarification(),
                session.getSafetyAllowedActions().stream().sorted().toList(),
                safety.steps(session)
            ),
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

    private void persistAdjustmentSignals(
        CheckinSessionEntity session,
        com.momna.modules.checkin.scoring.CheckinScoringResult result,
        Instant now
    ) {
        adjustmentSignals.deleteBySessionId(session.getSessionId());
        var completion = session.getCompletionRatio();
        for (var contribution : result.contributions()) {
            if (contribution.adjustmentContribution().signum() <= 0) continue;
            var signalId = sha(
                session.getSessionId() + "|" + contribution.itemCode() + "|"
                    + session.getDefinitionVersion() + "|" + session.getRulesVersion()
            );
            adjustmentSignals.save(new CheckinAdjustmentSignalEntity(
                signalId,
                session.getUserId(),
                session.getSessionId(),
                contribution.itemCode(),
                "checkin.adjustment." + contribution.itemCode().toLowerCase(Locale.ROOT),
                session.getPhase().name(),
                session.getLocalDate(),
                session.getPeriodAtTime(),
                contribution.statePoints(),
                result.adjustmentTier().name(),
                contribution.adjustmentContribution(),
                completion,
                session.getRulesVersion(),
                session.getDefinitionVersion(),
                now
            ));
        }
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
