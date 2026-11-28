package com.momna.modules.medicalsafety.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.application.CheckinApplicationService;
import com.momna.modules.checkin.application.CheckinDayContextService;
import com.momna.modules.medicalsafety.infrastructure.MedicalSafetyIdempotencyEntity;
import com.momna.modules.medicalsafety.infrastructure.MedicalSafetyIdempotencyId;
import com.momna.modules.medicalsafety.infrastructure.MedicalSafetyIdempotencyRepository;
import com.momna.modules.myday.application.MyDayOnboardingFactsPort;
import com.momna.modules.myday.application.template.MyDayFacts.OnboardingFact;
import com.momna.platform.safety.MedicalSafetyCatalog;
import com.momna.platform.safety.MedicalSafetyEr0Catalog;
import com.momna.platform.safety.MedicalSafetyException;
import com.momna.platform.safety.MedicalSafetyFlowService;
import com.momna.platform.safety.MedicalSafetyFlowService.GlobalStep;
import com.momna.platform.safety.MedicalStep;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The global v2.2 safety surfaces (MOMNA-1145): ER0 "I feel very unwell now" on My Day, Calendar and Check-in,
 * the one-time postpartum WB1 card, and explicit calendar signals. The period is always the server's; a route a
 * global entry joins in an open Check-in question updates that session's decision.
 */
@Service
public class MedicalSafetyApplicationService {
    /** WB1 is shown once between day 28 and day 42 after birth (catalog §9.4: about four weeks). */
    public static final int WB1_FIRST_DAY = 28;
    public static final int WB1_LAST_DAY = 42;
    static final String BIRTH_DATE = "onboarding.postpartum.birth_date";
    static final String TIME_SINCE_BIRTH = "onboarding.postpartum.time_since_birth_approx";

    /** A global action's result: the route step (null when WB1 just closes) and the joined Check-in session. */
    public record StepView(MedicalStep step, String checkinSessionId) {}

    /** The WB1 card: whether to show it now, and its texts. */
    public record Wb1View(String catalogVersion, boolean show, boolean alreadyAnswered, String answer,
        Map<String, Object> screen) {}

    private final MedicalSafetyFlowService flow;
    private final MedicalSafetyCatalog catalog;
    private final MedicalSafetyEr0Catalog er0;
    private final CheckinDayContextService contexts;
    private final ObjectProvider<CheckinApplicationService> checkin;
    private final ObjectProvider<MyDayOnboardingFactsPort> onboarding;
    private final MedicalSafetyIdempotencyRepository idempotency;
    private final ObjectMapper json;
    private final Clock clock = Clock.systemUTC();

    public MedicalSafetyApplicationService(MedicalSafetyFlowService flow, MedicalSafetyCatalog catalog,
        MedicalSafetyEr0Catalog er0, CheckinDayContextService contexts, ObjectProvider<CheckinApplicationService> checkin,
        ObjectProvider<MyDayOnboardingFactsPort> onboarding, MedicalSafetyIdempotencyRepository idempotency,
        ObjectMapper json) {
        this.flow = flow;
        this.catalog = catalog;
        this.er0 = er0;
        this.contexts = contexts;
        this.checkin = checkin;
        this.onboarding = onboarding;
        this.idempotency = idempotency;
        this.json = json;
    }

    /** The user's lifecycle period now, as the server knows it. */
    @Transactional(readOnly = true)
    public String lifecyclePeriod(String userId) {
        return period(userId);
    }

    private String period(String userId) {
        return contexts.resolve(userId, clock.instant()).period().name();
    }

    @Transactional(readOnly = true)
    public MedicalSafetyEr0Catalog.Screen er0Screen(String userId) {
        return er0.screen(period(userId));
    }

    @Transactional
    public StepView emergency(String userId, String selectionCode, String key) {
        if (selectionCode == null || selectionCode.isBlank()) {
            throw new MedicalSafetyException("SAFETY_VALUE_INVALID", "An ER0 sign is required");
        }
        return idempotent(userId, "er0", key, selectionCode, () -> {
            var period = period(userId);
            if ("NONE".equals(selectionCode)) {
                var shown = er0.screen(period).options().stream().map(MedicalSafetyEr0Catalog.Option::code)
                    .filter(code -> code.startsWith("F_")).toList();
                return flow.emergencyNone(userId, period, shown);
            }
            var option = er0.option(period, selectionCode);
            return flow.emergencyEntry(userId, period, option.routeId(), option.code());
        });
    }

    @Transactional
    public StepView answer(String userId, String routeId, String factCode, String answer, String key) {
        return idempotent(userId, "route-answer", key, routeId + "|" + factCode + "|" + answer,
            () -> flow.answerRoute(userId, routeId, factCode, answer));
    }

    /** «Not now» on a route a global entry started (L10). */
    @Transactional
    public StepView skip(String userId, String routeId, String key) {
        return idempotent(userId, "route-skip", key, routeId, () -> flow.skipRoute(userId, routeId));
    }

    @Transactional
    public StepView bloodPressure(String userId, String routeId, Integer systolic, Integer diastolic, boolean unknown,
        String key) {
        return idempotent(userId, "route-blood-pressure", key, routeId + "|" + systolic + "|" + diastolic + "|" + unknown,
            () -> flow.routeBloodPressure(userId, routeId, systolic, diastolic, unknown));
    }

    @Transactional
    public StepView calendarSignal(String userId, String signal, String key) {
        return idempotent(userId, "calendar-signal", key, String.valueOf(signal),
            () -> flow.calendarSignal(userId, period(userId), signal));
    }

    @Transactional(readOnly = true)
    public Wb1View wellbeing(String userId) {
        var period = period(userId);
        boolean inWindow = "POSTPARTUM".equals(period) && daysSinceBirth(userId)
            .map(days -> days >= WB1_FIRST_DAY && days <= WB1_LAST_DAY).orElse(false);
        var status = flow.wellbeingStatus(userId, inWindow);
        com.fasterxml.jackson.databind.JsonNode screen = null;
        for (var s : catalog.publicScreens(period).path("screens")) {
            if ("WB1".equals(s.path("code").asText())) screen = s;
        }
        return new Wb1View(status.catalogVersion(), status.show(), status.alreadyAnswered(), status.answer(),
            plain(screen));
    }

    /** A catalog node as plain maps and lists, so any JSON writer renders it. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> plain(com.fasterxml.jackson.databind.JsonNode node) {
        return node == null ? null : json.convertValue(node, Map.class);
    }

    @Transactional
    public StepView answerWellbeing(String userId, String answer, String key) {
        return idempotent(userId, "wb1", key, String.valueOf(answer),
            () -> flow.answerWellbeing(userId, period(userId), answer));
    }

    /**
     * Days since birth on the user's local date: the onboarding birth date, or, without it, the middle of the
     * approximate answer counted from when it was given ("less than a month" as two weeks, "month k" as k months
     * and two weeks; V41 codes, as on the design's wheel).
     */
    Optional<Long> daysSinceBirth(String userId) {
        var port = onboarding.getIfAvailable();
        if (port == null) return Optional.empty();
        var day = contexts.resolve(userId, clock.instant());
        Map<String, OnboardingFact> facts = port.facts(userId, "POSTPARTUM", Set.of(BIRTH_DATE, TIME_SINCE_BIRTH));
        var birth = facts.get(BIRTH_DATE) == null ? Optional.<LocalDate>empty() : facts.get(BIRTH_DATE).scalar()
            .flatMap(MedicalSafetyApplicationService::date);
        if (birth.isEmpty() && facts.get(TIME_SINCE_BIRTH) != null) {
            var approx = facts.get(TIME_SINCE_BIRTH);
            var answeredOn = approx.answeredAt() == null ? null : approx.answeredAt().atZone(day.timezone()).toLocalDate();
            var code = approx.scalar().orElse("");
            Integer ageDays = rangeMiddleDays(code);
            if (answeredOn != null && ageDays != null) birth = Optional.of(answeredOn.minusDays(ageDays));
        }
        return birth.map(b -> ChronoUnit.DAYS.between(b, day.localDate()));
    }

    /** The middle of a time-since-birth answer (V41 codes), in days; null for anything else. */
    static Integer rangeMiddleDays(String code) {
        if (code.equals("less_than_month")) return 14;
        if (code.matches("month_([1-9]|1[01])")) return Integer.parseInt(code.substring(6)) * 30 + 15;
        return null;
    }

    private static Optional<LocalDate> date(String value) {
        try {
            return Optional.of(LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    /** Runs a global action once per key; joins the Check-in session's decision when the route lives there. */
    private StepView idempotent(String userId, String operation, String key, String request, Supplier<GlobalStep> action) {
        if (key == null || key.isBlank() || key.length() > 160) {
            throw new IllegalArgumentException("A valid Idempotency-Key is required");
        }
        var fingerprint = sha(operation + "|" + request);
        var id = new MedicalSafetyIdempotencyId(userId, operation, key);
        var previous = idempotency.findById(id).orElse(null);
        if (previous != null) {
            if (!previous.getFingerprintSha256().equals(fingerprint)) {
                throw new MedicalSafetyException("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with another request");
            }
            try {
                return json.readValue(previous.getResponse(), StepView.class);
            } catch (Exception unreadable) {
                throw new IllegalStateException("Stored safety response unreadable", unreadable);
            }
        }
        var result = action.get();
        if (result.sessionId() != null) {
            var service = checkin.getIfAvailable();
            if (service != null) service.refreshSafety(userId, result.sessionId());
        }
        var view = new StepView(result.step(), result.sessionId());
        try {
            idempotency.save(new MedicalSafetyIdempotencyEntity(userId, operation, key, fingerprint,
                json.writeValueAsString(view), clock.instant()));
        } catch (Exception unwritable) {
            throw new IllegalStateException("Safety response could not be stored", unwritable);
        }
        return view;
    }

    private static String sha(String value) {
        try {
            var out = new StringBuilder(64);
            for (var b : MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))) {
                out.append("%02x".formatted(b & 0xff));
            }
            return out.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
