package com.momna.platform.safety;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable Check-in orchestration over the v2.2 resolver (MOMNA-1145): D12 direct entry, the cumulative
 * {@code safety_score >= 12} entry as a second path, L13 pauses, and the per-fact, checklist, blood-pressure,
 * correction and skip answers. Every decision comes from {@link MedicalSafetyResolver}; this class only stores
 * which facts a user answered on which route.
 *
 * <p>It knows Check-in only through {@link Trigger}s, so the platform does not depend on the Check-in module.
 */
@Service
public class MedicalSafetyFlowService {
    public static final Set<String> ANSWERS = Set.of("YES", "NO", "UNSURE", "ABSENT");
    /** PP2 "Emotional wellbeing": position 1 is "emotionally hard"; position 2 "can't tell yet" is not (D12.3). */
    static final String PP2 = "PP2";

    /** One answered Check-in item and its value (null when cleared). */
    public record ItemAnswer(String itemCode, Integer value) {}

    /** A safety-bearing contribution of the cumulative score, strongest first. */
    public record ScoreContribution(String itemCode, java.math.BigDecimal safetyContribution) {}

    /** What one Check-in answer change brings to the flow. */
    public record Trigger(
        String userId,
        String sessionId,
        String period,
        List<ItemAnswer> changes,
        Map<String, Integer> answers,
        boolean safetyThresholdReached,
        List<ScoreContribution> contributions
    ) {
        public Trigger {
            changes = List.copyOf(changes);
            answers = Map.copyOf(answers);
            contributions = List.copyOf(contributions);
        }
    }

    private final MedicalSafetyCatalog catalog;
    private final MedicalSafetyResolver resolver;
    private final MedicalSafetyRouteStateRepository states;
    private final MedicalSafetyFactRevisionRepository revisions;
    private final Clock clock;

    @Autowired
    public MedicalSafetyFlowService(MedicalSafetyCatalog catalog, MedicalSafetyResolver resolver,
        MedicalSafetyRouteStateRepository states, MedicalSafetyFactRevisionRepository revisions) {
        this(catalog, resolver, states, revisions, Clock.systemUTC());
    }

    public MedicalSafetyFlowService(MedicalSafetyCatalog catalog, MedicalSafetyResolver resolver,
        MedicalSafetyRouteStateRepository states, MedicalSafetyFactRevisionRepository revisions, Clock clock) {
        this.catalog = catalog;
        this.resolver = resolver;
        this.states = states;
        this.revisions = revisions;
        this.clock = clock;
    }

    public MedicalSafetyCatalog catalog() { return catalog; }

    /** D12 state that opens a route directly: 2, except PP2 where it is 1. */
    public static boolean directEntry(String itemCode, Integer value) {
        if (value == null) return false;
        return PP2.equals(itemCode) ? value == 1 : value == 2;
    }

    @Transactional
    public List<MedicalStep> onCheckinPatch(Trigger trigger) {
        var now = clock.instant();
        var period = trigger.period();
        var userId = trigger.userId();
        // A lowered answer closes the question it opened (without YES) and records the recovery that later
        // lets a worsening bypass the pause (L13.5b).
        for (var change : trigger.changes()) {
            if (directEntry(change.itemCode(), change.value())) continue;
            for (var route : catalog.forItem(period, change.itemCode())) {
                states.findByUserIdAndRouteId(userId, route.routeId()).ifPresent(state -> {
                    state.observeRecovery(change.itemCode(), now);
                    if (state.question() && trigger.sessionId().equals(state.getSessionId())
                        && Objects.equals(state.getSourceItem(), change.itemCode())
                        && !state.getFactAnswers().containsValue("YES")) {
                        state.update(resolver.evaluateMedical(route, catalog, state.getFactAnswers(), Map.of(), true),
                            now, route.cooldownHours());
                    }
                    states.save(state);
                });
            }
        }
        var candidates = new LinkedHashMap<String, Candidate>();
        for (var change : trigger.changes()) {
            for (var route : catalog.applicable(period, change.itemCode(), change.value())) {
                candidates.putIfAbsent(route.routeId(), new Candidate(route, change.itemCode()));
            }
        }
        // The cumulative entry stays an independent second path (D12.2). PP2 at 2 is uncertainty and never
        // opens R18 this way either.
        if (trigger.safetyThresholdReached()) {
            trigger.contributions().stream()
                .filter(c -> c.safetyContribution().signum() > 0)
                .filter(c -> !PP2.equals(c.itemCode()) || directEntry(PP2, trigger.answers().get(PP2)))
                .sorted(Comparator.comparing(ScoreContribution::safetyContribution).reversed())
                .flatMap(c -> catalog.forItem(period, c.itemCode()).stream()
                    .map(route -> new Candidate(route, c.itemCode())))
                .findFirst()
                .ifPresent(c -> candidates.putIfAbsent(c.route().routeId(), c));
        }
        for (var candidate : candidates.values()) {
            var route = candidate.route();
            var record = states.findByUserIdAndRouteId(userId, route.routeId())
                .orElseGet(() -> new MedicalSafetyRouteStateEntity(userId, route.routeId(), now));
            if (!record.mayStart(candidate.sourceItem(), trigger.sessionId(), now)) continue;
            record.begin(trigger.sessionId(), candidate.sourceItem(), now);
            record.update(resolver.evaluateMedical(route, catalog, record.getFactAnswers(),
                sharedFacts(userId, trigger.sessionId(), route.routeId()), false), now, route.cooldownHours());
            states.saveAndFlush(record);
        }
        return steps(userId, trigger.sessionId());
    }

    /**
     * The routes a session shows: the ones it opened, and a completed warning with YES from an earlier session
     * while its pause lasts (L13.5: a pause never hides a YES).
     */
    @Transactional(readOnly = true)
    public List<MedicalStep> steps(String userId, String sessionId) {
        var now = clock.instant();
        var result = new ArrayList<MedicalStep>();
        for (var record : sorted(states.findByUserId(userId))) {
            boolean own = sessionId.equals(record.getSessionId());
            boolean standingWarning = record.complete() && record.getFactAnswers().containsValue("YES")
                && record.getCooldownUntil() != null && now.isBefore(record.getCooldownUntil());
            if (!own && !standingWarning) continue;
            result.add(current(record, userId, sessionId));
        }
        return List.copyOf(result);
    }

    /** Only the routes this session opened: what the session's own SafetyDecision is made of. */
    @Transactional(readOnly = true)
    public List<MedicalStep> sessionSteps(String userId, String sessionId) {
        var result = new ArrayList<MedicalStep>();
        for (var record : sorted(states.findByUserIdAndSessionId(userId, sessionId))) {
            result.add(current(record, userId, sessionId));
        }
        return List.copyOf(result);
    }

    @Transactional
    public List<MedicalStep> answer(String userId, String sessionId, String routeId, String factCode, String value) {
        requireAnswer(value);
        if (factCode == null) throw new MedicalSafetyException("SAFETY_FACT_NOT_CURRENT", "Missing safety fact");
        fact(factCode);
        var route = route(routeId);
        var record = owned(userId, sessionId, routeId);
        if (record.complete()) {
            // An identical replay is harmless; a completed warning is never silently changed (use revise).
            if (value.equals(record.getFactAnswers().get(factCode))) return steps(userId, sessionId);
            throw new MedicalSafetyException("SAFETY_ROUTE_COMPLETED", "Safety route already completed");
        }
        var before = current(record, userId, sessionId);
        if (!before.question() || !factCode.equals(before.nextFactCode())) {
            throw new MedicalSafetyException("SAFETY_FACT_NOT_CURRENT", "Safety fact is not the current question");
        }
        var now = clock.instant();
        record.answer(factCode, value, now);
        record.update(current(record, userId, sessionId), now, route.cooldownHours());
        states.saveAndFlush(record);
        return steps(userId, sessionId);
    }

    /** A user corrects an answer she gave; the old answer stays in the audit trail. */
    @Transactional
    public List<MedicalStep> revise(String userId, String sessionId, String routeId, String factCode, String value) {
        requireAnswer(value);
        if (factCode == null) throw new MedicalSafetyException("SAFETY_FACT_NOT_CURRENT", "Missing safety fact");
        fact(factCode);
        var route = route(routeId);
        var record = owned(userId, sessionId, routeId);
        var old = record.getFactAnswers().get(factCode);
        if (old == null) throw new MedicalSafetyException("SAFETY_FACT_NOT_ANSWERED", "Fact has not been answered yet");
        if (old.equals(value)) return steps(userId, sessionId);
        var now = clock.instant();
        revisions.save(new MedicalSafetyFactRevisionEntity(userId, routeId, sessionId, factCode, old, value, now));
        record.reopenForCorrection(now);
        record.answer(factCode, value, now);
        record.update(current(record, userId, sessionId), now, route.cooldownHours());
        states.saveAndFlush(record);
        return steps(userId, sessionId);
    }

    /**
     * QM is one screen, not five questions: SELECTED marks the chosen facts YES and the other shown facts NO;
     * NONE, UNSURE and SKIP set every shown fact to NO, UNSURE or ABSENT.
     */
    @Transactional
    public List<MedicalStep> answerChecklist(String userId, String sessionId, String routeId, String mode,
        List<String> selectedFactCodes) {
        if (mode == null || !Set.of("SELECTED", "NONE", "UNSURE", "SKIP").contains(mode)) {
            throw new MedicalSafetyException("SAFETY_VALUE_INVALID", "Unknown checklist answer");
        }
        var selected = selectedFactCodes == null ? List.<String>of() : selectedFactCodes;
        if (selected.size() != new HashSet<>(selected).size()) {
            throw new MedicalSafetyException("SAFETY_VALUE_INVALID", "Repeated checklist item");
        }
        var route = route(routeId);
        var record = owned(userId, sessionId, routeId);
        var current = current(record, userId, sessionId);
        if (!record.question() || !"QM".equals(current.screenCode()) || !"ANSWER_CHECKLIST".equals(current.actionCode())) {
            throw new MedicalSafetyException("SAFETY_FACT_NOT_CURRENT", "Checklist not currently requested");
        }
        var shown = current.questionFactCodes();
        if (!shown.containsAll(selected) || ("SELECTED".equals(mode) == selected.isEmpty())) {
            throw new MedicalSafetyException("SAFETY_VALUE_INVALID", "Invalid checklist selection");
        }
        var now = clock.instant();
        for (var factCode : shown) {
            record.answer(factCode, switch (mode) {
                case "NONE" -> "NO";
                case "UNSURE" -> "UNSURE";
                case "SKIP" -> "ABSENT";
                default -> selected.contains(factCode) ? "YES" : "NO";
            }, now);
        }
        record.update(current(record, userId, sessionId), now, route.cooldownHours());
        states.saveAndFlush(record);
        return steps(userId, sessionId);
    }

    @Transactional
    public List<MedicalStep> recordBloodPressure(String userId, String sessionId, String routeId,
        Integer systolic, Integer diastolic, boolean unknown) {
        var route = route(routeId);
        var record = owned(userId, sessionId, routeId);
        if (!"BP_READING".equals(current(record, userId, sessionId).nextFactCode())) {
            throw new MedicalSafetyException("SAFETY_FACT_NOT_CURRENT", "Blood pressure is not the current safety input");
        }
        if (!unknown && (systolic == null || diastolic == null || systolic <= 0 || diastolic <= 0
            || systolic > 300 || diastolic > 200 || diastolic >= systolic)) {
            throw new MedicalSafetyException("SAFETY_VALUE_INVALID", "A measured systolic and diastolic pressure are required");
        }
        var now = clock.instant();
        record.recordBloodPressure(systolic, diastolic, unknown, now);
        record.update(current(record, userId, sessionId), now, route.cooldownHours());
        states.saveAndFlush(record);
        return steps(userId, sessionId);
    }

    /** Closes the remaining questions as ABSENT; never synthesises NO. */
    @Transactional
    public List<MedicalStep> skip(String userId, String sessionId, String routeId) {
        var route = route(routeId);
        var record = owned(userId, sessionId, routeId);
        if (!record.complete()) {
            var shared = sharedFacts(userId, sessionId, routeId);
            var now = clock.instant();
            for (var code : route.questions()) {
                if (!record.getFactAnswers().containsKey(code) && !shared.containsKey(code)) {
                    record.answer(code, "ABSENT", now);
                }
            }
            record.update(resolver.evaluateMedical(route, catalog, record.getFactAnswers(), shared, true),
                now, route.cooldownHours());
            states.saveAndFlush(record);
        }
        return steps(userId, sessionId);
    }

    private MedicalStep current(MedicalSafetyRouteStateEntity record, String userId, String sessionId) {
        var route = catalog.route(record.getRouteId());
        return resolver.evaluateMedical(route, catalog, record.getFactAnswers(),
            sharedFacts(userId, sessionId, route.routeId()), record.complete());
    }

    private static List<MedicalSafetyRouteStateEntity> sorted(Collection<MedicalSafetyRouteStateEntity> records) {
        return records.stream()
            .sorted(Comparator.comparingInt((MedicalSafetyRouteStateEntity r) -> routeNumber(r.getRouteId())))
            .toList();
    }

    private static int routeNumber(String routeId) {
        try {
            return Integer.parseInt(routeId.substring(1));
        } catch (RuntimeException notNumbered) {
            return Integer.MAX_VALUE;
        }
    }

    private MedicalSafetyCatalog.Route route(String routeId) {
        try {
            return catalog.route(routeId);
        } catch (IllegalArgumentException unknown) {
            throw new MedicalSafetyException("SAFETY_ROUTE_NOT_CURRENT", "Unknown safety route");
        }
    }

    private void fact(String factCode) {
        try {
            catalog.fact(factCode);
        } catch (IllegalArgumentException unknown) {
            throw new MedicalSafetyException("SAFETY_FACT_NOT_CURRENT", "Unknown safety fact");
        }
    }

    private MedicalSafetyRouteStateEntity owned(String userId, String sessionId, String routeId) {
        var record = states.findByUserIdAndRouteId(userId, routeId)
            .orElseThrow(() -> new MedicalSafetyException("SAFETY_ROUTE_NOT_CURRENT", "Safety route not found"));
        if (!sessionId.equals(record.getSessionId())) {
            throw new MedicalSafetyException("SAFETY_ROUTE_NOT_CURRENT", "Safety route does not belong to the session");
        }
        return record;
    }

    /** Facts already answered on this session's other routes: a fact is never asked twice in a session. */
    private Map<String, String> sharedFacts(String userId, String sessionId, String exceptRouteId) {
        var result = new LinkedHashMap<String, String>();
        for (var record : sorted(states.findByUserIdAndSessionId(userId, sessionId))) {
            if (record.getRouteId().equals(exceptRouteId)) continue;
            record.getFactAnswers().forEach(result::putIfAbsent);
        }
        return Map.copyOf(result);
    }

    private static void requireAnswer(String value) {
        if (value == null || !ANSWERS.contains(value)) {
            throw new MedicalSafetyException("SAFETY_VALUE_INVALID", "Safety answer must be YES, NO, UNSURE or ABSENT");
        }
    }

    private record Candidate(MedicalSafetyCatalog.Route route, String sourceItem) {}

    /** Visible for tests. */
    Instant now() { return clock.instant(); }
}
