package com.momna.modules.myday.application;

import com.momna.modules.lifecycle.application.LifecycleQueryService;
import com.momna.modules.myday.infrastructure.*;
import com.momna.modules.profile.application.ProfileService;
import com.momna.shared.outbox.OutboxEventEntity;
import com.momna.shared.outbox.OutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The durable side of My Day: one personal day per (user, local date). A new day takes its
 * cards from the configured {@link MyDayCardSource}; a later Check-in for the same day only
 * updates provenance, so card identity and progress are never lost. Called by the Check-in
 * orchestrator, which consumes the source sessions after this returns.
 */
@Service
public class MyDayMaterializationService implements MyDayMaterializationPort {
    public static final String GENERATION_POLICY_VERSION = "myday.generation.v1";
    public static final String CACHE_FALLBACK_SOURCE = "CACHE_FALLBACK";
    /** Generation spec: cache TTL 90 days. */
    public static final Duration CACHE_TTL = Duration.ofDays(90);

    private final MyDayRepository days;
    private final MyDayCardRepository cards;
    private final OutboxEventRepository outbox;
    private final LifecycleQueryService lifecycle;
    private final ProfileService profiles;
    private final ObjectProvider<MyDayCardSource> cardSources;
    private final MyDayGenerationCacheRepository cache;
    private final Clock clock = Clock.systemUTC();

    public MyDayMaterializationService(
        MyDayRepository days,
        MyDayCardRepository cards,
        OutboxEventRepository outbox,
        LifecycleQueryService lifecycle,
        ProfileService profiles,
        ObjectProvider<MyDayCardSource> cardSources,
        MyDayGenerationCacheRepository cache
    ) {
        this.cache = cache;
        this.days = days;
        this.cards = cards;
        this.outbox = outbox;
        this.lifecycle = lifecycle;
        this.profiles = profiles;
        this.cardSources = cardSources;
    }

    @Override
    public Result upsert(Command command) {
        var now = clock.instant();
        var existing = days.findByUserIdAndLocalDate(command.userId(), command.localDate()).orElse(null);
        if (command.existingTargetMyDayId() != null
            && (existing == null || !existing.getMyDayId().equals(command.existingTargetMyDayId()))) {
            throw new MyDayException("MY_DAY_TARGET_CONFLICT", "Check-in points to another My Day");
        }
        if (existing != null) {
            if (existing.getOrchestrationId().equals(command.orchestrationId())) {
                return new Result(existing.getMyDayId(), "UNCHANGED", command.orchestrationId());
            }
            existing.recordOrchestration(command.orchestrationId(), command.orchestrationVersion(),
                command.baseline().share(), provenance(command), now);
            days.save(existing);
            return new Result(existing.getMyDayId(), "PROVENANCE_UPDATED", command.orchestrationId());
        }

        var period = lifecycle.current(command.userId(), now).primary();
        if (period == null) {
            throw new MyDayException("MY_DAY_CONTEXT_INSUFFICIENT", "Lifecycle period is required for My Day");
        }
        var periodCode = period.period().name();
        var lifeMode = MyDayCards.LIFE_MODES.get(periodCode);
        var language = language(command.userId());
        MyDayCards.GeneratedDay generated;
        boolean degraded = false;
        try {
            var source = cardSources.getIfAvailable();
            if (source == null) {
                throw new MyDayException("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", "No My Day card source is configured");
            }
            generated = source.generate(new MyDayCardSource.Input(
                command.userId(), command.localDate(), command.timezone(), periodCode, lifeMode, language,
                command.orchestrationId(), command.baseline().share(), command.sources(), command.adjustments()));
            MyDayCards.requireComplete(generated.cards());
            remember(generated, lifeMode, language, now);
        } catch (RuntimeException failure) {
            // Generation spec: an unavailable or invalid answer is never shown. Serve a verified day of
            // the same period and language if one exists, otherwise "not ready yet, retry".
            generated = similarVerifiedDay(lifeMode, language, now).orElseThrow(() -> new MyDayException(
                "MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", "My Day is not ready yet"));
            degraded = true;
        }

        var myDayId = UUID.randomUUID().toString();
        var day = new MyDayEntity(
            myDayId, command.userId(), command.localDate(), command.timezone(), periodCode, language,
            command.orchestrationId(), command.orchestrationVersion(), command.baseline().share(),
            provenance(command), generated.cardSource(), generated.generationPolicyVersion(),
            generated.promptVersion(), generated.modelRef(), generated.safetyVersion(), generated.cacheKey(), now);
        if (degraded) day.markDegraded();
        days.saveAndFlush(day);
        for (var card : generated.cards()) {
            cards.save(new MyDayCardEntity(myDayId, cardId(card.slot(), card.position()), card.slot(),
                card.position(), card.title(), card.microcopy(), card.whatToDo(), card.whyToday(),
                card.howToDo(), card.ifSkipped(), card.support()));
        }
        outbox.save(new OutboxEventEntity("myday-generated-" + myDayId, "MyDayGenerated", now, Map.of(
            "schemaVersion", 1,
            "myDayId", myDayId,
            "userId", command.userId(),
            "localDate", command.localDate().toString(),
            "cardCount", generated.cards().size(),
            "cardSource", generated.cardSource(),
            "degraded", degraded,
            "generationPolicyVersion", generated.generationPolicyVersion()
        )));
        return new Result(myDayId, "CREATED", command.orchestrationId());
    }

    /** Similar = same life mode and language, unexpired. Overlay matching joins once overlays exist. */
    private java.util.Optional<MyDayCards.GeneratedDay> similarVerifiedDay(String lifeMode, String language, Instant now) {
        return cache.findFirstByLifeModeAndLanguageAndExpiresAtAfterOrderByCreatedAtDesc(lifeMode, language, now)
            .map(entry -> new MyDayCards.GeneratedDay(
                entry.getCards().stream().map(MyDayMaterializationService::card).toList(),
                CACHE_FALLBACK_SOURCE, entry.getGenerationPolicyVersion(), entry.getPromptVersion(),
                entry.getModelRef(), entry.getSafetyVersion(), entry.getCacheKey()));
    }

    private void remember(MyDayCards.GeneratedDay generated, String lifeMode, String language, Instant now) {
        if (generated.cacheKey() == null || generated.cacheKey().isBlank()) return;
        var existing = cache.findById(generated.cacheKey()).orElse(null);
        if (existing != null && existing.getExpiresAt().isAfter(now)) return;
        cache.save(new MyDayGenerationCacheEntity(generated.cacheKey(), lifeMode, language,
            generated.cards().stream().map(MyDayMaterializationService::map).toList(), generated.cardSource(),
            generated.generationPolicyVersion(), generated.promptVersion(), generated.modelRef(),
            generated.safetyVersion(), now, now.plus(CACHE_TTL)));
    }

    private static Map<String, Object> map(MyDayCards.Card card) {
        var item = new LinkedHashMap<String, Object>();
        item.put("slot", card.slot());
        item.put("position", card.position());
        item.put("title", card.title());
        item.put("microcopy", card.microcopy());
        item.put("whatToDo", card.whatToDo());
        item.put("whyToday", card.whyToday());
        item.put("howToDo", card.howToDo());
        item.put("ifSkipped", card.ifSkipped());
        item.put("support", card.support());
        return item;
    }

    private static MyDayCards.Card card(Map<String, Object> item) {
        return new MyDayCards.Card((String) item.get("slot"), ((Number) item.get("position")).intValue(),
            (String) item.get("title"), (String) item.get("microcopy"), (String) item.get("whatToDo"),
            (String) item.get("whyToday"), (String) item.get("howToDo"), (String) item.get("ifSkipped"),
            (String) item.get("support"));
    }

    public static String cardId(String slot, int position) {
        return slot.toLowerCase(Locale.ROOT) + "-" + position;
    }

    /** Evening(D-1) and Morning(D) both target My Day(D); provenance keeps both dates explicit. */
    static Map<String, Object> provenance(Command command) {
        var sources = new ArrayList<Map<String, Object>>();
        for (var source : command.sources()) {
            var item = new LinkedHashMap<String, Object>();
            item.put("sourceType", source.sourceType());
            item.put("sourceRef", source.sourceRef());
            item.put("phase", source.phaseCode());
            item.put("sourceLocalDate", sourceLocalDate(source.phaseCode(), command.localDate()).toString());
            item.put("state", source.stateCode());
            item.put("role", source.bindingRole());
            item.put("completionRatio", source.completionRatio().toPlainString());
            item.put("baseShare", source.baseShare().toPlainString());
            item.put("effectiveShare", source.effectiveShare().toPlainString());
            item.put("definitionVersion", source.definitionVersion());
            item.put("rulesVersion", source.rulesVersion());
            sources.add(item);
        }
        var adjustments = new ArrayList<Map<String, Object>>();
        for (var signal : command.adjustments()) {
            var item = new LinkedHashMap<String, Object>();
            item.put("signalId", signal.signalId());
            item.put("signalCode", signal.signalCode());
            item.put("phase", signal.phaseCode());
            item.put("tier", signal.tierCode());
            item.put("scoreContribution", signal.scoreContribution().toPlainString());
            item.put("effectivePhaseWeight", signal.effectivePhaseWeight().toPlainString());
            item.put("rulesVersion", signal.rulesVersion());
            adjustments.add(item);
        }
        var baseline = new LinkedHashMap<String, Object>();
        baseline.put("share", command.baseline().share().toPlainString());
        baseline.put("mode", command.baseline().modeCode());
        baseline.put("bindingVersion", command.baseline().bindingVersion());
        var result = new LinkedHashMap<String, Object>();
        result.put("targetLocalDate", command.localDate().toString());
        result.put("orchestrationId", command.orchestrationId());
        result.put("orchestrationVersion", command.orchestrationVersion());
        result.put("baseline", baseline);
        result.put("sources", sources);
        result.put("adjustments", adjustments);
        return result;
    }

    static LocalDate sourceLocalDate(String phase, LocalDate target) {
        return "EVENING".equals(phase) ? target.minusDays(1) : target;
    }

    private String language(String userId) {
        var profile = profiles.get(userId);
        var preferred = profile == null ? null : profile.getPreferredLanguage();
        return preferred != null && preferred.toLowerCase(Locale.ROOT).startsWith("ru") ? "ru" : "en";
    }
}
