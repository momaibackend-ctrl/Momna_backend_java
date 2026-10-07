package com.momna.modules.checkin.myday;

import com.momna.modules.checkin.application.CheckinDayContextService;
import com.momna.modules.checkin.domain.*;
import com.momna.modules.checkin.infrastructure.*;
import com.momna.modules.myday.application.MyDayMaterializationPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinMyDayOrchestrator {
    public static final String VERSION = "checkin-myday-orchestration.v1";
    private static final int SCALE = 6;
    private static final BigDecimal MORNING_TWO_PHASE = new BigDecimal("0.700000");
    private static final BigDecimal EVENING_TWO_PHASE = new BigDecimal("0.300000");
    private static final BigDecimal FULL = new BigDecimal("1.000000");
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final CheckinDayContextService contexts;
    private final CheckinSessionRepository sessions;
    private final CheckinAdjustmentSignalRepository signals;
    private final ObjectProvider<MyDayMaterializationPort> myDay;

    public CheckinMyDayOrchestrator(
        CheckinDayContextService contexts,
        CheckinSessionRepository sessions,
        CheckinAdjustmentSignalRepository signals,
        ObjectProvider<MyDayMaterializationPort> myDay
    ) {
        this.contexts = contexts;
        this.sessions = sessions;
        this.signals = signals;
        this.myDay = myDay;
    }

    @Transactional
    public Result materialize(String userId, Instant referenceAt) {
        var day = contexts.resolve(userId, referenceAt);
        var sourceSessions = new ArrayList<CheckinSessionEntity>();

        for (var phase : CheckinPhase.values()) {
            sessions.findByUserIdAndPhaseAndLocalDateAndPeriodAtTime(
                userId, phase, day.localDate(), day.period().name()
            ).filter(this::isFinal).ifPresent(sourceSessions::add);
        }
        sourceSessions.sort(Comparator.comparing(x -> x.getPhase().ordinal()));

        if (sourceSessions.stream().anyMatch(CheckinSessionEntity::isSafetyBlocking)) {
            throw new CheckinMyDayOrchestrationException("SAFETY_BLOCKED", "Safety clarification blocks My Day");
        }

        var existingTargets = sourceSessions.stream()
            .map(CheckinSessionEntity::getConsumedByRef)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (existingTargets.size() > 1) {
            throw new CheckinMyDayOrchestrationException(
                "CONFLICTING_EXISTING_MY_DAY",
                "Check-in sessions point to different My Day targets"
            );
        }

        var scenario = scenario(sourceSessions);
        var phases = new ArrayList<PhaseProjection>();
        for (var session : sourceSessions) {
            var base = switch (scenario) {
                case "MORNING_EVENING" ->
                    session.getPhase() == CheckinPhase.MORNING ? MORNING_TWO_PHASE : EVENING_TWO_PHASE;
                default -> FULL;
            };
            var completion = decimal(session.getCompletionRatio());
            phases.add(new PhaseProjection(
                session.getPhase().name(),
                session.getSessionId(),
                session.getStatus().name(),
                session.getConsumedAt() == null ? "FRESH" : "HISTORICAL",
                completion,
                base,
                decimal(base.multiply(completion)),
                session.getDefinitionVersion(),
                session.getRulesVersion()
            ));
        }

        var totalEffective = phases.stream()
            .map(PhaseProjection::effectiveShare)
            .reduce(ZERO, BigDecimal::add);
        var baselineShare = decimal(FULL.subtract(totalEffective).max(ZERO));

        var evidence = new ArrayList<SignalProjection>();
        for (var phase : phases) {
            for (var signal : signals.findByUserIdAndSessionIdOrderBySignalIdAsc(userId, phase.sourceSessionId())) {
                if (signal.getScoreContribution().signum() <= 0) continue;
                evidence.add(new SignalProjection(
                    signal.getSignalId(),
                    signal.getSignalCode(),
                    signal.getSessionId(),
                    signal.getSourcePhase(),
                    signal.getTier(),
                    signal.getScoreContribution(),
                    signal.getCompletionRatio(),
                    phase.effectiveShare(),
                    signal.getRulesVersion(),
                    signal.getDefinitionVersion()
                ));
            }
        }
        evidence.sort(Comparator.comparing(SignalProjection::signalId));

        var existingTarget = existingTargets.isEmpty() ? null : existingTargets.getFirst();
        var orchestrationId = sha(orchestrationFingerprint(
            userId, day.localDate().toString(), scenario, phases, evidence
        ));

        var port = myDay.getIfAvailable();
        if (port == null) {
            return new Result(
                null,
                day.localDate().toString(),
                day.timezone().getId(),
                scenario,
                orchestrationId,
                VERSION,
                "TEMPORARILY_UNAVAILABLE",
                existingTarget,
                baselineShare,
                List.copyOf(phases),
                List.copyOf(evidence),
                Set.of()
            );
        }

        var materialized = port.upsert(new MyDayMaterializationPort.Command(
            userId,
            day.localDate(),
            day.timezone().getId(),
            existingTarget,
            orchestrationId,
            VERSION,
            orchestrationId,
            new MyDayMaterializationPort.BaselineReference(
                "pending-context-fingerprint",
                "pending-projection-fingerprint",
                "PERIOD_FALLBACK_REQUIRED",
                "checkin-baseline-bindings.v1",
                1, 1, 1,
                baselineShare
            ),
            phases.stream().map(x -> new MyDayMaterializationPort.SourceReference(
                "CHECKIN",
                x.sourceSessionId(),
                x.phase(),
                x.status(),
                x.sourceRole(),
                x.completionRatio(),
                x.baseShare(),
                x.effectiveShare(),
                String.valueOf(x.definitionVersion()),
                x.rulesVersion()
            )).toList(),
            evidence.stream().map(x -> new MyDayMaterializationPort.AdjustmentEvidence(
                x.signalId(),
                x.signalCode(),
                x.sourceSessionId(),
                x.sourcePhase(),
                x.tier(),
                x.scoreContribution(),
                x.completionRatio(),
                x.effectivePhaseWeight(),
                x.rulesVersion(),
                x.definitionVersion()
            )).toList()
        ));

        if (existingTarget != null && !existingTarget.equals(materialized.myDayId())) {
            throw new CheckinMyDayOrchestrationException(
                "TARGET_MY_DAY_MISMATCH",
                "Materialization returned another My Day target"
            );
        }

        var consumed = new LinkedHashSet<String>();
        for (var session : sourceSessions) {
            if (session.getConsumedAt() != null) continue;
            session.markConsumed(materialized.myDayId(), referenceAt);
            sessions.save(session);
            consumed.add(session.getPhase().name());
        }

        return new Result(
            materialized.myDayId(),
            day.localDate().toString(),
            day.timezone().getId(),
            scenario,
            orchestrationId,
            VERSION,
            materialized.outcome(),
            existingTarget,
            baselineShare,
            List.copyOf(phases),
            List.copyOf(evidence),
            Set.copyOf(consumed)
        );
    }

    private boolean isFinal(CheckinSessionEntity session) {
        return session.getStatus() == CheckinSessionStatus.SUBMITTED
            || session.getStatus() == CheckinSessionStatus.AUTO_FINALIZED;
    }

    private String scenario(List<CheckinSessionEntity> sessions) {
        var phases = sessions.stream().map(CheckinSessionEntity::getPhase).collect(java.util.stream.Collectors.toSet());
        if (phases.contains(CheckinPhase.MORNING) && phases.contains(CheckinPhase.EVENING)) return "MORNING_EVENING";
        if (phases.contains(CheckinPhase.MORNING)) return "MORNING_ONLY";
        if (phases.contains(CheckinPhase.EVENING)) return "EVENING_ONLY";
        return "BASELINE_ONLY";
    }

    private BigDecimal decimal(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private String orchestrationFingerprint(
        String userId,
        String localDate,
        String scenario,
        List<PhaseProjection> phases,
        List<SignalProjection> signals
    ) {
        var parts = new ArrayList<String>();
        parts.add(userId);
        parts.add(localDate);
        parts.add(scenario);
        parts.add(VERSION);
        phases.forEach(x -> parts.add(
            x.phase() + ":" + x.sourceSessionId() + ":" + x.status() + ":"
                + x.completionRatio() + ":" + x.effectiveShare() + ":"
                + x.definitionVersion() + ":" + x.rulesVersion()
        ));
        signals.forEach(x -> parts.add(x.signalId() + ":" + x.scoreContribution()));
        return String.join("|", parts);
    }

    private String sha(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            var out = new StringBuilder(64);
            for (byte item : bytes) out.append("%02x".formatted(item & 0xff));
            return out.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    public record PhaseProjection(
        String phase,
        String sourceSessionId,
        String status,
        String sourceRole,
        BigDecimal completionRatio,
        BigDecimal baseShare,
        BigDecimal effectiveShare,
        int definitionVersion,
        String rulesVersion
    ) {}

    public record SignalProjection(
        String signalId,
        String signalCode,
        String sourceSessionId,
        String sourcePhase,
        String tier,
        BigDecimal scoreContribution,
        BigDecimal completionRatio,
        BigDecimal effectivePhaseWeight,
        String rulesVersion,
        int definitionVersion
    ) {}

    public record Result(
        String myDayId,
        String localDate,
        String timezone,
        String scenario,
        String orchestrationId,
        String orchestrationVersion,
        String materializationOutcome,
        String existingTargetMyDayId,
        BigDecimal baselineShare,
        List<PhaseProjection> phases,
        List<SignalProjection> signals,
        Set<String> freshlyConsumedPhases
    ) {}
}
