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
    private final MedicalSafetyWb1ResponseRepository wellbeing;
    private final Clock clock = Clock.systemUTC();

    public MedicalSafetyFlowService(MedicalSafetyCatalog catalog,
        CanonicalSafetyService safety, MedicalSafetyRouteStateRepository states,
        MedicalSafetyWb1ResponseRepository wellbeing) {
        this.catalog = catalog;
        this.safety = safety;
        this.states = states;
        this.wellbeing = wellbeing;
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
                            if ("QUESTION".equals(state.getState())
                                && Objects.equals(state.getSourceItem(), change.itemCode())
                                && !state.getFactAnswers().containsValue("YES")) {
                                var done = safety.evaluateMedical(route, catalog,
                                    state.getFactAnswers(), Map.of(), true);
                                state.update(done, now, route.cooldownHours());
                            }
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
            if (!record.mayStart(source, session.getSessionId(), now)) continue;
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
        var records = states.findByUserId(userId).stream()
            .filter(record -> sessionId.equals(record.getSessionId())
                || ("COMPLETE".equals(record.getState())
                    && record.getFactAnswers().containsValue("YES")))
            .toList();
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
    public List<CanonicalSafetyService.MedicalStep> recordBloodPressure(
        String userId, String sessionId, String routeId,
        Integer systolic, Integer diastolic, boolean unknown
    ) {
        if (!"R17".equals(routeId)) throw new IllegalArgumentException("BP1 is only available on R17");
        var route = catalog.route(routeId);
        var record = owned(userId, sessionId, routeId);
        var before = safety.evaluateMedical(route, catalog, record.getFactAnswers(),
            sharedFacts(userId, sessionId, routeId), false);
        if (!"BP_READING".equals(before.nextFactCode())) {
            throw new IllegalArgumentException("Blood pressure is not the current safety input");
        }
        record.recordBloodPressure(systolic, diastolic, unknown, clock.instant());
        var next = safety.evaluateMedical(route, catalog, record.getFactAnswers(),
            sharedFacts(userId, sessionId, routeId), false);
        // Thresholds from the addendum are NOT clinically approved.
        // They are not used to reclassify recorded pressure measurements.
        record.update(next, clock.instant(), route.cooldownHours());
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

    /** Eligibility is intentionally gated on the product-defined postpartum date window. */
    @Transactional(readOnly = true)
    public Wb1Status wellbeingStatus(String userId, String period) {
        var recorded = wellbeing.findById(userId).orElse(null);
        var previousMentalHealth = states.findByUserIdAndRouteId(userId, "R18").orElse(null);
        boolean recentRoute = previousMentalHealth != null && previousMentalHealth.getCompletedAt() != null
            && clock.instant().isBefore(previousMentalHealth.getCompletedAt().plusSeconds(14 * 86400L));
        return new Wb1Status("medical-safety-v2.2", recorded != null,
            recorded != null ? recorded.getAnswer() : null,
            period.equals("POSTPARTUM") && recorded == null && !recentRoute,
            "PRODUCT_WINDOW_NOT_CONFIGURED");
    }

    @Transactional
    public CanonicalSafetyService.MedicalStep answerWellbeing(String userId,
        String period, String answer) {
        if (!"POSTPARTUM".equals(period)) {
            throw new IllegalArgumentException("WB1 is only applicable after childbirth");
        }
        if (answer == null || !Set.of("MOSTLY_GOOD", "HARD_LATELY",
            "PREFER_NOT_TO_SAY").contains(answer)) {
            throw new IllegalArgumentException("Invalid wellbeing card answer");
        }
        var existing = wellbeing.findById(userId).orElse(null);
        if (existing != null) {
            if (!existing.getAnswer().equals(answer))
                throw new IllegalStateException("WB1 was already answered and cannot be repeated");
            if ("HARD_LATELY".equals(answer)) {
                var saved = states.findByUserIdAndRouteId(userId, "R18").orElse(null);
                if (saved != null && "WB1".equals(saved.getSourceItem())) {
                    return safety.evaluateMedical(catalog.route("R18"), catalog,
                        saved.getFactAnswers(), Map.of(), saved.getState().equals("COMPLETE"));
                }
            }
            return new CanonicalSafetyService.MedicalStep(
                "R18", "POSTPARTUM_MENTAL_HEALTH",
                MedicalSafetyCatalog.VERSION, MedicalSafetyCatalog.RULE_VERSION,
                "COMPLETE", "NOT_REPORTED", "H1", "OPEN_ER0", null,
                "WB1_ALREADY_ANSWERED");
        }
        var now = clock.instant();
        wellbeing.saveAndFlush(new MedicalSafetyWb1ResponseEntity(userId, answer, now));
        if (!"HARD_LATELY".equals(answer)) {
            return new CanonicalSafetyService.MedicalStep(
                "R18", "POSTPARTUM_MENTAL_HEALTH",
                MedicalSafetyCatalog.VERSION, MedicalSafetyCatalog.RULE_VERSION,
                "COMPLETE", "NOT_REPORTED", "H1", "OPEN_ER0", null,
                "WB1_ONE_TIME_CLOSED");
        }
        var route = catalog.route("R18");
        var record = states.findByUserIdAndRouteId(userId, route.routeId())
            .orElseGet(() -> new MedicalSafetyRouteStateEntity(userId, route.routeId(), now));
        if (record.getCompletedAt() != null && now.isBefore(
            record.getCompletedAt().plusSeconds(route.cooldownHours() * 3600L))) {
            return new CanonicalSafetyService.MedicalStep(
                route.routeId(), route.routeCode(), MedicalSafetyCatalog.VERSION,
                MedicalSafetyCatalog.RULE_VERSION, "COMPLETE", "NOT_REPORTED", "H1",
                "OPEN_ER0", null, "WB1_R18_COOLDOWN_ACTIVE");
        }
        record.begin(null, "WB1", now);
        var next = safety.evaluateMedical(route, catalog, record.getFactAnswers(), Map.of(), false);
        record.update(next, now, route.cooldownHours());
        states.saveAndFlush(record);
        return next;
    }

    @Transactional
    public CanonicalSafetyService.MedicalStep answerWellbeingFact(String userId, String factCode,
        String answer) {
        if (factCode == null || answer == null || !Set.of("YES", "NO", "UNSURE", "ABSENT").contains(answer)) {
            throw new IllegalArgumentException("Invalid WB1 safety answer");
        }
        var route = catalog.route("R18");
        var record = states.findByUserIdAndRouteId(userId, "R18")
            .orElseThrow(() -> new IllegalArgumentException("WB1 route not started"));
        if (record.getSessionId() != null || !"WB1".equals(record.getSourceItem())) {
            throw new IllegalArgumentException("WB1 is not current safety route");
        }
        var previous = safety.evaluateMedical(route, catalog, record.getFactAnswers(), Map.of(), false);
        if (record.getState().equals("COMPLETE")) {
            if (answer.equals(record.getFactAnswers().get(factCode))) return previous;
            throw new IllegalStateException("WB1 route already completed");
        }
        if (!"QUESTION".equals(previous.state()) || !factCode.equals(previous.nextFactCode())) {
            throw new IllegalArgumentException("Not the current WB1 safety question");
        }
        record.answer(factCode, answer, clock.instant());
        var next = safety.evaluateMedical(route, catalog, record.getFactAnswers(), Map.of(), false);
        record.update(next, clock.instant(), route.cooldownHours());
        states.saveAndFlush(record);
        return next;
    }

    public record Wb1Status(String catalogVersion, boolean alreadyAnswered, String answer,
        boolean stageAndCooldownEligible, String displayWindowStatus) {}

    private static final Set<String> ER0_GLOBAL = Set.of(
        "F_BREATHING_SEVERE", "F_CHEST_PAIN", "F_FAINTED", "F_SEIZURE",
        "F_NEURO_DEFICIT", "F_THUNDERCLAP_HEADACHE", "F_HEART_RACING",
        "F_PAD_SOAK_1H", "F_HARM_THOUGHTS", "ALLERGY_SWELLING"
    );
    private static final Set<String> ER0_PREGNANCY = Set.of(
        "F_PREG_VAGINAL_BLEED", "F_FLUID_LEAK", "F_REDUCED_MOVEMENTS"
    );
    private static final Set<String> ER0_POSTPARTUM = Set.of(
        "F_BLEED_SUDDEN_INCREASE", "F_ONE_LIMB", "POSTPARTUM_PREECLAMPSIA"
    );

    /** Global one-tap ER0 event. Unlike ordinary daily check-in, never cooled down. */
    @Transactional
    public CanonicalSafetyService.MedicalStep emergencyEntry(String userId, String period,
        String selectionCode) {
        if (selectionCode == null || (!ER0_GLOBAL.contains(selectionCode)
            && !(period.equals("PREGNANCY") && ER0_PREGNANCY.contains(selectionCode))
            && !(period.equals("POSTPARTUM") && ER0_POSTPARTUM.contains(selectionCode)))) {
            throw new IllegalArgumentException("ER0 selection not available in this period");
        }
        var routeId = "ALLERGY_SWELLING".equals(selectionCode) ? "R25"
            : "POSTPARTUM_PREECLAMPSIA".equals(selectionCode) ? "R17" : "R26";
        var route = catalog.route(routeId);
        var now = clock.instant();
        var record = states.findByUserIdAndRouteId(userId, routeId)
            .orElseGet(() -> new MedicalSafetyRouteStateEntity(userId, routeId, now));
        record.begin(null, "ER0:" + selectionCode, now);
        if (selectionCode.startsWith("F_")) {
            record.answer(selectionCode, "YES", now);
        }
        var step = safety.evaluateMedical(route, catalog, record.getFactAnswers(),
            Map.of(), false, period);
        record.update(step, now, 0);
        states.saveAndFlush(record);
        return step;
    }

    @Transactional
    public CanonicalSafetyService.MedicalStep emergencyBloodPressure(String userId,
        Integer systolic, Integer diastolic, boolean unknown) {
        var record = states.findByUserIdAndRouteId(userId, "R17")
            .orElseThrow(() -> new IllegalArgumentException("ER0 R17 route not active"));
        if (record.getSessionId() != null || record.getSourceItem() == null
            || !record.getSourceItem().startsWith("ER0:")) {
            throw new IllegalArgumentException("Not an ER0 blood-pressure route");
        }
        var route = catalog.route("R17");
        var before = safety.evaluateMedical(route, catalog, record.getFactAnswers(), Map.of(), false);
        if (!"BP_READING".equals(before.nextFactCode())) {
            throw new IllegalArgumentException("Blood pressure is not currently requested");
        }
        record.recordBloodPressure(systolic, diastolic, unknown, clock.instant());
        var next = safety.evaluateMedical(route, catalog, record.getFactAnswers(), Map.of(), false);
        record.update(next, clock.instant(), 0);
        states.saveAndFlush(record);
        return next;
    }

    @Transactional
    public CanonicalSafetyService.MedicalStep answerEmergency(String userId,
        String routeId, String factCode, String value) {
        if (!routeId.equals("R25") && !routeId.equals("R17")) {
            throw new IllegalArgumentException("This ER0 route has no follow-up questions");
        }
        if (!Set.of("YES", "NO", "UNSURE", "ABSENT").contains(value)) {
            throw new IllegalArgumentException("Invalid safety answer");
        }
        var route = catalog.route(routeId);
        var record = states.findByUserIdAndRouteId(userId, routeId)
            .orElseThrow(() -> new IllegalArgumentException("ER0 route not found"));
        if (record.getSessionId() != null || !record.getSourceItem().startsWith("ER0:")) {
            throw new IllegalArgumentException("ER0 route is not active");
        }
        var before = safety.evaluateMedical(route, catalog, record.getFactAnswers(),
            Map.of(), false);
        if (!"QUESTION".equals(before.state()) || !factCode.equals(before.nextFactCode())) {
            throw new IllegalArgumentException("Safety fact is not the current question");
        }
        record.answer(factCode, value, clock.instant());
        var after = safety.evaluateMedical(route, catalog, record.getFactAnswers(),
            Map.of(), false);
        record.update(after, clock.instant(), 0);
        states.saveAndFlush(record);
        return after;
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
