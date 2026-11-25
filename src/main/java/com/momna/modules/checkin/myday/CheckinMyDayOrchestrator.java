package com.momna.modules.checkin.myday;

import com.momna.modules.checkin.application.CheckinDayContextService;
import com.momna.modules.checkin.domain.*;
import com.momna.modules.checkin.infrastructure.*;
import com.momna.modules.myday.application.MyDayMaterializationPort;
import com.momna.modules.myday.application.template.MyDayFacts;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinMyDayOrchestrator {
    public static final String VERSION = "checkin-myday-orchestration.v2";
    private static final int SCALE = 6;
    private static final BigDecimal MORNING_TWO_PHASE = new BigDecimal("0.700000");
    private static final BigDecimal EVENING_TWO_PHASE = new BigDecimal("0.300000");
    private static final BigDecimal FULL = new BigDecimal("1.000000");
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final CheckinDayContextService contexts;
    private final CheckinSessionRepository sessions;
    private final CheckinAdjustmentSignalRepository signals;
    private final CheckinAnswerRepository answers;
    private final ObjectProvider<MyDayMaterializationPort> myDay;
    private final ObjectProvider<com.momna.platform.safety.MedicalSafetyFlowService> medicalSafety;

    public CheckinMyDayOrchestrator(
        CheckinDayContextService contexts,
        CheckinSessionRepository sessions,
        CheckinAdjustmentSignalRepository signals,
        CheckinAnswerRepository answers,
        ObjectProvider<MyDayMaterializationPort> myDay,
        ObjectProvider<com.momna.platform.safety.MedicalSafetyFlowService> medicalSafety
    ) {
        this.medicalSafety = medicalSafety;
        this.contexts = contexts;
        this.sessions = sessions;
        this.signals = signals;
        this.answers = answers;
        this.myDay = myDay;
    }

    /**
     * One generation per (user, local date) (MOMNA-989, 23.11.2026): the sources are fixed when My Day(D)
     * is generated. Once the day exists it is returned unchanged and no session is consumed by it later.
     */
    @Transactional
    public Result materialize(String userId, Instant referenceAt) {
        var day = contexts.resolve(userId, referenceAt);
        var port = myDay.getIfAvailable();
        var stored = port == null ? Optional.<String>empty() : port.findMyDayId(userId, day.localDate());
        if (stored.isPresent()) {
            return new Result(stored.get(), day.localDate().toString(), day.timezone().getId(), null, null,
                VERSION, "EXISTING", stored.get(), null, List.of(), List.of(), Set.of());
        }
        var sourceSessions = finalSources(userId, day, referenceAt);

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

        // Typed facts for the overlay adapter (MOMNA-1324): the answered items with their phase's base
        // share, and each source's authoritative SafetyDecision. Nothing here is recomputed.
        var observations = new ArrayList<MyDayFacts.Observation>();
        for (var phase : phases) {
            for (var answer : answers.findBySessionIdOrderByItemCodeAsc(phase.sourceSessionId())) {
                observations.add(new MyDayFacts.Observation(phase.phase(), answer.getItemCode(), answer.getValue(),
                    phase.baseShare()));
            }
        }
        var safety = new ArrayList<>(sourceSessions.stream()
            .filter(s -> s.getSafetyRouteCode() != null)
            .map(s -> new MyDayFacts.SafetyProjection(s.getPhase().name(), s.getSafetyRouteCode(),
                s.getSafetySeverity(), s.getSafetyDecisionVersion()))
            .toList());
        // The global entries (ER0, WB1, calendar; MOMNA-1145) decided outside a Check-in on this local day
        // or the one before it are part of the day too.
        var global = medicalSafety.getIfAvailable();
        if (global != null) {
            var from = day.localDate().minusDays(1).atStartOfDay(day.timezone()).toInstant();
            var to = day.localDate().plusDays(1).atStartOfDay(day.timezone()).toInstant();
            for (var step : global.globalOutcomes(userId, from, to)) {
                safety.add(new MyDayFacts.SafetyProjection("GLOBAL", step.routeCode(), step.severity(),
                    step.catalogVersion() + ":" + step.ruleVersion()));
            }
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
            )).toList(),
            observations,
            safety
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

    /**
     * Whether Evening(D-1) would take part in a My Day(D) generated now: a final session with at least
     * one answer. It decides the second choice of screen A ("Get My Plan" instead of "Get General Plan"),
     * so the label always matches the plan that choice produces.
     */
    @Transactional
    public boolean eveningContributes(String userId, Instant referenceAt) {
        var day = contexts.resolve(userId, referenceAt);
        return finalSources(userId, day, referenceAt).stream()
            .anyMatch(s -> s.getPhase() == CheckinPhase.EVENING && s.getCompletionRatio().signum() > 0);
    }

    /**
     * Final Morning(D) and Evening(D-1). A morning left mutable after its window closed is finalised here
     * first, exactly as reading it would, so the result does not depend on which request came first.
     */
    private List<CheckinSessionEntity> finalSources(String userId, CheckinDayContextService.DayContext day,
                                                    Instant referenceAt) {
        var sourceSessions = new ArrayList<CheckinSessionEntity>();
        // My Day D is informed by the previous evening (D-1) and today's morning (D).
        // Keep the session's own local date unchanged; only its My Day target is D.
        sessions.findByUserIdAndPhaseAndLocalDateAndPeriodAtTime(
            userId, CheckinPhase.MORNING, day.localDate(), day.period().name()
        ).map(morning -> {
            if (!isFinal(morning) && !morning.isSafetyBlocking() && !referenceAt.isBefore(morning.getWindowClosesAt())) {
                morning.autoFinalize(referenceAt);
                return sessions.saveAndFlush(morning);
            }
            return morning;
        }).filter(this::isFinal).ifPresent(sourceSessions::add);
        sessions.findByUserIdAndPhaseAndLocalDate(
            userId, CheckinPhase.EVENING, sourceDateFor(CheckinPhase.EVENING, day.localDate())
        ).filter(this::isFinal).ifPresent(sourceSessions::add);
        sourceSessions.sort(Comparator.comparing(x -> x.getPhase().ordinal()));
        return sourceSessions;
    }

    static LocalDate sourceDateFor(CheckinPhase phase, LocalDate targetDate) {
        return phase == CheckinPhase.EVENING ? targetDate.minusDays(1) : targetDate;
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
