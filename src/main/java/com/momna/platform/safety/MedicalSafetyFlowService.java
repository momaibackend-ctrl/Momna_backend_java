package com.momna.platform.safety;

import com.momna.modules.checkin.application.CheckinViews.AnswerChange;
import com.momna.modules.checkin.infrastructure.CheckinSessionEntity;
import com.momna.modules.checkin.scoring.CheckinScoringResult;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thin durable orchestration over CORE-BE-12's canonical Safety resolver.
 * All medical decisions come from CanonicalSafetyService and the v2.2 catalog.
 */
@Service
public class MedicalSafetyFlowService {
    private final MedicalSafetyCatalog catalog;
    private final CanonicalSafetyService safety;
    private final MedicalSafetyRouteStateRepository states;
    private final Clock clock = Clock.systemUTC();

    public MedicalSafetyFlowService(MedicalSafetyCatalog catalog,
        CanonicalSafetyService safety, MedicalSafetyRouteStateRepository states) {
        this.catalog = catalog;
        this.safety = safety;
        this.states = states;
    }

    @Transactional
    public List<CanonicalSafetyService.MedicalStep> onCheckinPatch(
        CheckinSessionEntity session, List<AnswerChange> changes, Map<String, Integer> answers,
        CheckinScoringResult score
    ) {
        var now = clock.instant();
        var period = session.getPeriodAtTime();
        var userId = session.getUserId();
        for (var change : changes) {
            for (var route : catalog.forItem(period, change.itemCode())) {
                var active = "PP2".equals(change.itemCode())
                    ? Integer.valueOf(1).equals(change.value())
                    : Integer.valueOf(2).equals(change.value());
                if (!active) {
                    states.findByUserIdAndRouteId(userId, route.routeId())
                        .ifPresent(state -> {
                            state.observeRecovery(change.itemCode(), now);
                            states.save(state);
                        });
                }
            }
        }
        var candidates = new LinkedHashMap<String, MedicalSafetyCatalog.Route>();
        for (var change : changes) {
            for (var route : catalog.applicable(period, change.itemCode(), change.value())) {
                candidates.putIfAbsent(route.routeId(), route);
            }
        }
        // Preserve the original cumulative entry as a second, independent path.
        if (score.safetyThresholdReferenceReached()) {
            score.contributions().stream()
                .filter(contribution -> contribution.safetyContribution().signum() > 0)
                .sorted(Comparator
                    .comparing(CheckinScoringResult.Contribution::safetyContribution)
                    .reversed())
                .map(contribution -> catalog.forItem(period, contribution.itemCode()))
                .flatMap(Collection::stream)
                .findFirst()
                .ifPresent(route -> candidates.putIfAbsent(route.routeId(), route));
        }
        for (var route : candidates.values()) {
            var source = changes.stream()
                .filter(change -> catalog.applicable(period, change.itemCode(), change.value())
                    .contains(route))
                .map(AnswerChange::itemCode).findFirst().orElseGet(() -> score.contributions()
                    .stream().filter(c -> catalog.forItem(period, c.itemCode()).contains(route))
                    .map(CheckinScoringResult.Contribution::itemCode).findFirst().orElse("SCORE"));
            var record = states.findByUserIdAndRouteId(userId, route.routeId())
                .orElseGet(() -> new MedicalSafetyRouteStateEntity(userId, route.routeId(), now));
            if (!record.mayStart(source, now)) continue;
            record.begin(session.getSessionId(), source, now);
            var next = safety.evaluateMedical(route, catalog, record.getFactAnswers(),
                sharedFacts(userId, session.getSessionId(), route.routeId()), false);
            record.update(next, now, route.cooldownHours());
            states.saveAndFlush(record);
        }
        return steps(userId, session.getSessionId());
    }

    @Transactional(readOnly = true)
    public List<CanonicalSafetyService.MedicalStep> steps(String userId, String sessionId) {
        var records = states.findByUserIdAndSessionId(userId, sessionId);
        var result = new ArrayList<CanonicalSafetyService.MedicalStep>();
        for (var record : records) {
            var route = catalog.route(record.getRouteId());
            result.add(safety.evaluateMedical(route, catalog, record.getFactAnswers(),
                sharedFacts(userId, sessionId, route.routeId()), record.getState().equals("COMPLETE")));
        }
        return List.copyOf(result);
    }

    @Transactional
    public List<CanonicalSafetyService.MedicalStep> answer(String userId, String sessionId,
        String routeId, String factCode, String value
    ) {
        var route = catalog.route(routeId);
        if (factCode == null || value == null) throw new IllegalArgumentException("Missing safety answer");
        catalog.fact(factCode);
        if (!Set.of("YES", "NO", "UNSURE", "ABSENT").contains(value)) {
            throw new IllegalArgumentException("Invalid safety response");
        }
        var record = owned(userId, sessionId, routeId);
        var before = safety.evaluateMedical(route, catalog, record.getFactAnswers(),
            sharedFacts(userId, sessionId, routeId), false);
        if (record.getState().equals("COMPLETE")) {
            // Safe idempotent replay; a completed warning is never silently cleared.
            if (value.equals(record.getFactAnswers().get(factCode))) return steps(userId, sessionId);
            throw new IllegalStateException("Safety route already completed");
        }
        if (!"QUESTION".equals(before.state()) || !factCode.equals(before.nextFactCode())) {
            throw new IllegalArgumentException("Safety fact is not the current question");
        }
        record.answer(factCode, value, clock.instant());
        var after = safety.evaluateMedical(route, catalog, record.getFactAnswers(),
            sharedFacts(userId, sessionId, routeId), false);
        record.update(after, clock.instant(), route.cooldownHours());
        states.saveAndFlush(record);
        return steps(userId, sessionId);
    }

    @Transactional
    public List<CanonicalSafetyService.MedicalStep> skip(String userId, String sessionId,
        String routeId) {
        var record = owned(userId, sessionId, routeId);
        var route = catalog.route(routeId);
        var shared = sharedFacts(userId, sessionId, routeId);
        if (!"COMPLETE".equals(record.getState())) {
            for (var code : route.questions()) {
                if (!record.getFactAnswers().containsKey(code) && !shared.containsKey(code)) {
                    record.answer(code, "ABSENT", clock.instant());
                }
            }
            var next = safety.evaluateMedical(route, catalog, record.getFactAnswers(), shared, true);
            record.update(next, clock.instant(), route.cooldownHours());
            states.saveAndFlush(record);
        }
        return steps(userId, sessionId);
    }

    private MedicalSafetyRouteStateEntity owned(String userId, String sessionId, String routeId) {
        var record = states.findByUserIdAndRouteId(userId, routeId)
            .orElseThrow(() -> new IllegalArgumentException("Safety route not found"));
        if (!sessionId.equals(record.getSessionId())) {
            throw new IllegalArgumentException("Safety route does not belong to the session");
        }
        return record;
    }

    private Map<String, String> sharedFacts(String userId, String sessionId, String exceptRouteId) {
        var result = new LinkedHashMap<String, String>();
        for (var record : states.findByUserIdAndSessionId(userId, sessionId)) {
            if (record.getRouteId().equals(exceptRouteId)) continue;
            // Only already answered facts from this session; never synthesize NO.
            record.getFactAnswers().forEach(result::putIfAbsent);
        }
        return Map.copyOf(result);
    }
}
