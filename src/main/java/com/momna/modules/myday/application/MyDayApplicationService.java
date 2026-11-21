package com.momna.modules.myday.application;

import static com.momna.modules.myday.application.MyDayViews.*;

import com.momna.modules.checkin.application.CheckinDayContextService;
import com.momna.modules.checkin.myday.CheckinMyDayOrchestrationException;
import com.momna.modules.checkin.myday.CheckinMyDayOrchestrator;
import com.momna.modules.myday.infrastructure.*;
import com.momna.shared.outbox.OutboxEventEntity;
import com.momna.shared.outbox.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-only My Day API: generate, read, and card progress. */
@Service
public class MyDayApplicationService {
    private static final Set<String> FINAL_CARD_STATES = Set.of("COMPLETED", "SKIPPED");
    private static final Set<String> GENERATE_SOURCES = Set.of("HOME", "CHECKIN_HANDOFF", "RETRY");
    /** MyDayCompleted: every AM and PM card is COMPLETED or SKIPPED; optional cards do not count. */
    private static final Set<String> COMPLETION_SLOTS = Set.of("AM", "PM");

    private final CheckinMyDayOrchestrator orchestrator;
    private final CheckinDayContextService dayContexts;
    private final MyDayRepository days;
    private final MyDayCardRepository cards;
    private final MyDayIdempotencyRepository idempotency;
    private final OutboxEventRepository outbox;
    private final Clock clock = Clock.systemUTC();

    public MyDayApplicationService(
        CheckinMyDayOrchestrator orchestrator,
        CheckinDayContextService dayContexts,
        MyDayRepository days,
        MyDayCardRepository cards,
        MyDayIdempotencyRepository idempotency,
        OutboxEventRepository outbox
    ) {
        this.orchestrator = orchestrator;
        this.dayContexts = dayContexts;
        this.days = days;
        this.cards = cards;
        this.idempotency = idempotency;
        this.outbox = outbox;
    }

    /**
     * Idempotent by nature: one My Day per user and local date. Evening(D-1) and Morning(D) feed
     * My Day(D) through the Check-in orchestrator, which consumes them only after the day is stored.
     */
    @Transactional
    public GenerateResult generate(String userId, String source, String idempotencyKey) {
        requireKey(idempotencyKey);
        var requestSource = source == null || source.isBlank() ? "HOME" : source;
        if (!GENERATE_SOURCES.contains(requestSource)) {
            throw new IllegalArgumentException("source must be HOME, CHECKIN_HANDOFF or RETRY");
        }
        var fingerprint = sha("generate|" + requestSource);
        var replay = idempotency.findById(new MyDayIdempotencyId(userId, "generate", idempotencyKey)).orElse(null);
        if (replay != null) {
            if (!replay.getFingerprintSha256().equals(fingerprint)) {
                throw new MyDayException("IDEMPOTENCY_CONFLICT", "Idempotency-Key was used for another request");
            }
            return new GenerateResult(view(owned(userId, replay.getResultMyDayId())), "EXISTING");
        }
        CheckinMyDayOrchestrator.Result result;
        try {
            result = orchestrator.materialize(userId, clock.instant());
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
        idempotency.save(new MyDayIdempotencyEntity(userId, "generate", idempotencyKey, fingerprint,
            result.myDayId(), clock.instant()));
        return new GenerateResult(view(owned(userId, result.myDayId())), result.materializationOutcome());
    }

    @Transactional(readOnly = true)
    public TodayView today(String userId) {
        var day = dayContexts.resolve(userId, clock.instant());
        return days.findByUserIdAndLocalDate(userId, day.localDate())
            .map(found -> new TodayView(found.getStatus(), day.localDate().toString(), day.timezone().getId(), view(found)))
            .orElseGet(() -> new TodayView("NOT_GENERATED", day.localDate().toString(), day.timezone().getId(), null));
    }

    @Transactional(readOnly = true)
    public MyDayView get(String userId, String myDayId) {
        return view(owned(userId, myDayId));
    }

    @Transactional
    public MyDayView updateCard(String userId, String myDayId, String cardId, long expectedRevision,
                                String status, String idempotencyKey) {
        requireKey(idempotencyKey);
        if (status == null || !FINAL_CARD_STATES.contains(status)) {
            throw new MyDayException("MY_DAY_CARD_STATUS_INVALID", "Card status must be COMPLETED or SKIPPED");
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
        if (!"PENDING".equals(card.getStatus())) {
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
        outbox.save(new OutboxEventEntity(
            "myday-card-" + myDayId + "-" + cardId,
            "COMPLETED".equals(status) ? "MyDayCardCompleted" : "MyDayCardSkipped",
            now,
            Map.of("schemaVersion", 1, "myDayId", myDayId, "userId", userId, "cardId", cardId,
                "slot", card.getSlot(), "localDate", day.getLocalDate().toString())));
        publishCompletionOnce(day, now);
        idempotency.save(new MyDayIdempotencyEntity(userId, "card", idempotencyKey, fingerprint, myDayId, now));
        return view(day);
    }

    /**
     * AM and PM cards never return to PENDING, so the day completes exactly once: on the update
     * that finalises the last of them. The deterministic event id keeps it once per day.
     */
    private void publishCompletionOnce(MyDayEntity day, java.time.Instant at) {
        var counted = cards.findByMyDayIdOrderBySlotAscPositionAsc(day.getMyDayId()).stream()
            .filter(c -> COMPLETION_SLOTS.contains(c.getSlot()))
            .toList();
        if (counted.isEmpty() || !counted.stream().allMatch(c -> FINAL_CARD_STATES.contains(c.getStatus()))) return;
        var eventId = "myday-completed-" + day.getMyDayId();
        if (outbox.existsById(eventId)) return;
        long completed = counted.stream().filter(c -> "COMPLETED".equals(c.getStatus())).count();
        outbox.save(new OutboxEventEntity(eventId, "MyDayCompleted", at, Map.of(
            "schemaVersion", 1, "myDayId", day.getMyDayId(), "userId", day.getUserId(),
            "localDate", day.getLocalDate().toString(),
            "completedCount", completed, "skippedCount", counted.size() - completed)));
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

    MyDayView view(MyDayEntity day) {
        var slotOrder = MyDayCards.SLOTS;
        var cardViews = cards.findByMyDayIdOrderBySlotAscPositionAsc(day.getMyDayId()).stream()
            .sorted(Comparator.comparingInt((MyDayCardEntity c) -> slotOrder.indexOf(c.getSlot()))
                .thenComparingInt(MyDayCardEntity::getPosition))
            .map(c -> new CardView(c.getCardId(), c.getSlot(), c.getPosition(), c.getTitle(), c.getMicrocopy(),
                new DetailsView(c.getWhatToDo(), c.getWhyToday(), c.getHowToDo(), c.getIfSkipped(), c.getSupport()),
                c.getStatus(), c.getStatusChangedAt()))
            .toList();
        int completed = (int) cardViews.stream().filter(c -> "COMPLETED".equals(c.status())).count();
        int skipped = (int) cardViews.stream().filter(c -> "SKIPPED".equals(c.status())).count();
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
            cardViews,
            new ProgressView(cardViews.size(), completed, skipped, cardViews.size() - completed - skipped),
            provenance(day)
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
