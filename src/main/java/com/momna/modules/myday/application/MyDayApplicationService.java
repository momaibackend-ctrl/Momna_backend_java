package com.momna.modules.myday.application;

import static com.momna.modules.myday.application.MyDayViews.*;

import com.momna.modules.checkin.application.CheckinDayContextService;
import com.momna.modules.checkin.myday.CheckinMyDayOrchestrationException;
import com.momna.modules.checkin.myday.CheckinMyDayOrchestrator;
import com.momna.modules.myday.infrastructure.*;
import com.momna.modules.profile.application.ProfileService;
import com.momna.shared.outbox.OutboxEventEntity;
import com.momna.shared.outbox.OutboxEventRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-only My Day API: generate, read, and card progress. One successful generation per
 * (user, local date); see {@link MyDayPlanRules} for which plan a user gets.
 */
@Service
public class MyDayApplicationService {
    /** STANDARD = a plan without the morning Check-in; HOME is its earlier name, still accepted. */
    private static final Set<String> GENERATE_SOURCES = Set.of("STANDARD", "CHECKIN_HANDOFF", "RETRY");

    private final CheckinMyDayOrchestrator orchestrator;
    private final CheckinDayContextService dayContexts;
    private final MyDayRepository days;
    private final MyDayCardRepository cards;
    private final MyDayIdempotencyRepository idempotency;
    private final OutboxEventRepository outbox;
    private final ProfileService profiles;
    private final Clock clock = Clock.systemUTC();

    public MyDayApplicationService(
        CheckinMyDayOrchestrator orchestrator,
        CheckinDayContextService dayContexts,
        MyDayRepository days,
        MyDayCardRepository cards,
        MyDayIdempotencyRepository idempotency,
        OutboxEventRepository outbox,
        ProfileService profiles
    ) {
        this.orchestrator = orchestrator;
        this.dayContexts = dayContexts;
        this.days = days;
        this.cards = cards;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.profiles = profiles;
    }

    /**
     * Idempotent by nature: one My Day per user and local date, generated once. Evening(D-1) and
     * Morning(D) feed My Day(D) through the Check-in orchestrator, which consumes them only after the
     * day is stored. The source only names the screen the request came from; the plan's kind follows
     * the Check-ins that actually took part.
     */
    @Transactional
    public GenerateResult generate(String userId, String source, String idempotencyKey) {
        requireKey(idempotencyKey);
        var requestSource = source == null || source.isBlank() || "HOME".equals(source) ? "STANDARD" : source;
        if (!GENERATE_SOURCES.contains(requestSource)) {
            throw new IllegalArgumentException("source must be STANDARD, CHECKIN_HANDOFF or RETRY");
        }
        var fingerprint = sha("generate|" + requestSource);
        var replay = idempotency.findById(new MyDayIdempotencyId(userId, "generate", idempotencyKey)).orElse(null);
        if (replay != null) {
            if (!replay.getFingerprintSha256().equals(fingerprint)) {
                throw new MyDayException("IDEMPOTENCY_CONFLICT", "Idempotency-Key was used for another request");
            }
            return new GenerateResult(view(owned(userId, replay.getResultMyDayId())), "EXISTING");
        }
        var result = materialize(userId, clock.instant());
        idempotency.save(new MyDayIdempotencyEntity(userId, "generate", idempotencyKey, fingerprint,
            result.myDayId(), clock.instant()));
        return new GenerateResult(view(owned(userId, result.myDayId())), result.materializationOutcome());
    }

    /**
     * The current local date. GET does not generate, with one exception decided by Product on
     * 23.11.2026: when the morning window has closed and there is no plan, the first open starts the
     * plan without a morning Check-in. Each call runs its own transaction, so a failed generation
     * leaves nothing behind and the screen shows choice A with the failure text.
     */
    public TodayView today(String userId) {
        var now = clock.instant();
        var day = dayContexts.resolve(userId, now);
        var date = day.localDate().toString();
        var zone = day.timezone().getId();
        var found = days.findByUserIdAndLocalDate(userId, day.localDate()).orElse(null);
        String failure = null;
        if (found == null && "CLOSED".equals(day.morning().stateAt(now))) {
            try {
                materialize(userId, now);
            } catch (MyDayException notGenerated) {
                // A safety clarification is answered in Check-in first; screen A then shows no error.
                // Any other failure is shown on screen A with Retry, never as a failed screen.
                if (!"MY_DAY_SAFETY_BLOCKED".equals(notGenerated.code())) {
                    failure = MyDayTexts.of(language(userId)).generationFailure();
                }
            } catch (DataIntegrityViolationException concurrentCreate) {
                // Another request stored the day first; it is read below.
            }
            found = days.findByUserIdAndLocalDate(userId, day.localDate()).orElse(null);
        }
        if (found != null) {
            return new TodayView(found.getStatus(), date, zone, view(found), null, null);
        }
        return new TodayView("NOT_GENERATED", date, zone, null, planChoice(userId, now), failure);
    }

    @Transactional(readOnly = true)
    public MyDayView get(String userId, String myDayId) {
        return view(owned(userId, myDayId));
    }

    /**
     * Card progress toggles both ways (design D-7). MyDayCompleted is published once per day, when
     * every AM and PM card is COMPLETED, and is not withdrawn when a mark is taken back.
     */
    @Transactional
    public MyDayView updateCard(String userId, String myDayId, String cardId, long expectedRevision,
                                String status, String idempotencyKey) {
        requireKey(idempotencyKey);
        if (status == null || !MyDayPlanRules.CARD_STATES.contains(status)) {
            throw new MyDayException("MY_DAY_CARD_STATUS_INVALID", "Card status must be COMPLETED, PENDING or SKIPPED");
        }
        var fingerprint = sha("card|" + myDayId + "|" + cardId + "|" + expectedRevision + "|" + status);
        var replay = idempotency.findById(new MyDayIdempotencyId(userId, "card", idempotencyKey)).orElse(null);
        if (replay != null) {
            if (!replay.getFingerprintSha256().equals(fingerprint)) {
                throw new MyDayException("IDEMPOTENCY_CONFLICT", "Idempotency-Key was used for another request");
            }
            return view(owned(userId, replay.getResultMyDayId()));
        }

        var day = owned(userId, myDayId);
        if (day.getRevision() != expectedRevision) {
            throw new MyDayException("STALE_REVISION", "My Day revision conflict");
        }
        var card = cards.findById(new MyDayCardId(myDayId, cardId))
            .orElseThrow(() -> new MyDayException("MY_DAY_CARD_NOT_FOUND", "My Day card not found"));
        if (!MyDayPlanRules.allowedTransition(card.getStatus(), status)) {
            throw new MyDayException("MY_DAY_CARD_STATE_CONFLICT", "My Day card is already " + card.getStatus());
        }
        var now = clock.instant();
        card.changeStatus(status, now);
        cards.save(card);
        day.touch(now);
        try {
            day = days.saveAndFlush(day);
        } catch (OptimisticLockingFailureException conflict) {
            throw new MyDayException("STALE_REVISION", "My Day revision conflict");
        }
        var eventType = switch (status) {
            case MyDayPlanRules.COMPLETED -> "MyDayCardCompleted";
            case MyDayPlanRules.SKIPPED -> "MyDayCardSkipped";
            default -> "MyDayCardReopened";
        };
        // One event per change: a card may now be completed, reopened and completed again.
        outbox.save(new OutboxEventEntity(
            "myday-card-" + myDayId + "-" + cardId + "-r" + day.getRevision(),
            eventType,
            now,
            Map.of("schemaVersion", 1, "myDayId", myDayId, "userId", userId, "cardId", cardId,
                "slot", card.getSlot(), "localDate", day.getLocalDate().toString())));
        publishCompletionOnce(day, now);
        idempotency.save(new MyDayIdempotencyEntity(userId, "card", idempotencyKey, fingerprint, myDayId, now));
        return view(day);
    }

    private CheckinMyDayOrchestrator.Result materialize(String userId, Instant at) {
        CheckinMyDayOrchestrator.Result result;
        try {
            result = orchestrator.materialize(userId, at);
        } catch (CheckinMyDayOrchestrationException failure) {
            throw switch (failure.code()) {
                case "SAFETY_BLOCKED" -> new MyDayException("MY_DAY_SAFETY_BLOCKED",
                    "A Check-in safety clarification must be answered first");
                default -> new MyDayException("MY_DAY_TARGET_CONFLICT", "Check-in sources point to another My Day");
            };
        }
        if (result.myDayId() == null) {
            throw new MyDayException("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", "My Day materialization is unavailable");
        }
        return result;
    }

    private PlanChoiceView planChoice(String userId, Instant at) {
        var texts = MyDayTexts.of(language(userId));
        var secondary = MyDayPlanRules.secondaryChoice(orchestrator.eveningContributes(userId, at));
        var standard = MyDayPlanRules.SECONDARY_FROM_EVENING.equals(secondary) ? texts.fromEvening() : texts.general();
        return new PlanChoiceView(secondary,
            new ChoiceView(texts.personal().title(), texts.personal().description()),
            new ChoiceView(standard.title(), standard.description()));
    }

    /** The event id is deterministic, so the day completes exactly once even if a mark is undone and redone. */
    private void publishCompletionOnce(MyDayEntity day, Instant at) {
        var all = cards.findByMyDayIdOrderBySlotAscPositionAsc(day.getMyDayId());
        var status = new LinkedHashMap<String, String>();
        var slot = new LinkedHashMap<String, String>();
        all.forEach(c -> {
            status.put(c.getCardId(), c.getStatus());
            slot.put(c.getCardId(), c.getSlot());
        });
        if (!MyDayPlanRules.dayComplete(status, slot)) return;
        var eventId = "myday-completed-" + day.getMyDayId();
        if (outbox.existsById(eventId)) return;
        long counted = all.stream().filter(c -> MyDayPlanRules.COMPLETION_SLOTS.contains(c.getSlot())).count();
        outbox.save(new OutboxEventEntity(eventId, "MyDayCompleted", at, Map.of(
            "schemaVersion", 1, "myDayId", day.getMyDayId(), "userId", day.getUserId(),
            "localDate", day.getLocalDate().toString(), "completedCount", counted)));
    }

    private static void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
    }

    private MyDayEntity owned(String userId, String myDayId) {
        return days.findByMyDayIdAndUserId(myDayId, userId)
            .orElseThrow(() -> new MyDayException("MY_DAY_NOT_FOUND", "My Day not found"));
    }

    private String language(String userId) {
        var profile = profiles.get(userId);
        var preferred = profile == null ? null : profile.getPreferredLanguage();
        return preferred != null && preferred.toLowerCase(Locale.ROOT).startsWith("ru") ? "ru" : "en";
    }

    MyDayView view(MyDayEntity day) {
        var texts = MyDayTexts.of(day.getLanguage());
        var headers = texts.sectionHeaders();
        var slotOrder = MyDayCards.SLOTS;
        var cardViews = cards.findByMyDayIdOrderBySlotAscPositionAsc(day.getMyDayId()).stream()
            .sorted(Comparator.comparingInt((MyDayCardEntity c) -> slotOrder.indexOf(c.getSlot()))
                .thenComparingInt(MyDayCardEntity::getPosition))
            .map(c -> new CardView(c.getCardId(), c.getSlot(), c.getPosition(), c.getTitle(), c.getMicrocopy(),
                new DetailsView(c.getWhatToDo(), c.getWhyToday(), c.getHowToDo(), c.getIfSkipped(), c.getSupport()),
                List.of(
                    new SectionView(headers.get(0), c.getWhatToDo()),
                    new SectionView(headers.get(1), c.getWhyToday()),
                    new SectionView(headers.get(2), c.getHowToDo()),
                    new SectionView(headers.get(3), c.getIfSkipped())),
                new NoteView(texts.noteBadge(), null, c.getSupport()),
                c.getStatus(), c.getStatusChangedAt()))
            .toList();
        int completed = (int) cardViews.stream().filter(c -> MyDayPlanRules.COMPLETED.equals(c.status())).count();
        int skipped = (int) cardViews.stream().filter(c -> MyDayPlanRules.SKIPPED.equals(c.status())).count();
        var provenance = provenance(day);
        var kind = MyDayPlanRules.kind(provenance.sources().stream()
            .map(s -> s.effectiveShare() == null ? BigDecimal.ZERO : new BigDecimal(s.effectiveShare()))
            .toList());
        return new MyDayView(
            day.getMyDayId(),
            day.getLocalDate().toString(),
            day.getTimezoneAtGeneration(),
            day.getStatus(),
            day.getLifecyclePeriod(),
            day.getLanguage(),
            day.getRevision(),
            day.getGeneratedAt(),
            day.getUpdatedAt(),
            kind,
            MyDayPlanRules.PERSONAL.equals(kind) ? texts.personalBadge() : null,
            cardViews,
            new ProgressView(cardViews.size(), completed, skipped, cardViews.size() - completed - skipped),
            provenance
        );
    }

    @SuppressWarnings("unchecked")
    private ProvenanceView provenance(MyDayEntity day) {
        var raw = day.getProvenance();
        var sources = ((List<Map<String, Object>>) raw.getOrDefault("sources", List.of())).stream()
            .map(s -> new SourceView(str(s.get("phase")), str(s.get("sourceLocalDate")), str(s.get("state")),
                str(s.get("completionRatio")), str(s.get("effectiveShare"))))
            .toList();
        return new ProvenanceView(str(raw.get("targetLocalDate")), day.getBaselineShare(), sources,
            day.getCardSource(), day.getGenerationPolicyVersion(), day.getPromptVersion(), day.getModelRef());
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static String sha(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            var out = new StringBuilder(64);
            for (byte b : bytes) out.append("%02x".formatted(b & 0xff));
            return out.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
