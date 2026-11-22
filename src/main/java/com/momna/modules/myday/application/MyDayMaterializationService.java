package com.momna.modules.myday.application;

import com.momna.modules.lifecycle.application.LifecycleQueryService;
import com.momna.modules.myday.infrastructure.*;
import com.momna.modules.profile.application.ProfileService;
import com.momna.shared.outbox.OutboxEventEntity;
import com.momna.shared.outbox.OutboxEventRepository;
import java.time.Clock;
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

    private final MyDayRepository days;
    private final MyDayCardRepository cards;
    private final OutboxEventRepository outbox;
    private final LifecycleQueryService lifecycle;
    private final ProfileService profiles;
    private final ObjectProvider<MyDayCardSource> cardSources;
    private final Clock clock = Clock.systemUTC();

    public MyDayMaterializationService(
        MyDayRepository days,
        MyDayCardRepository cards,
        OutboxEventRepository outbox,
        LifecycleQueryService lifecycle,
        ProfileService profiles,
        ObjectProvider<MyDayCardSource> cardSources
    ) {
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
        // An unavailable source or an invalid answer is never shown: 503 "not ready yet", retry.
        // There is no cross-user fallback: a generated day may carry personal context, and only an
        // anonymous template (no user, no Check-in details) could ever be shared. See CORE-BE-13.
        MyDayCards.GeneratedDay generated;
        try {
            var source = cardSources.getIfAvailable();
            if (source == null) throw notReady();
            generated = source.generate(new MyDayCardSource.Input(
                command.userId(), command.localDate(), command.timezone(), periodCode, lifeMode, language,
                command.orchestrationId(), command.baseline().share(), command.sources(), command.adjustments()));
            MyDayCards.requireComplete(generated.cards());
        } catch (RuntimeException failure) {
            throw notReady();
        }

        var myDayId = UUID.randomUUID().toString();
        var day = new MyDayEntity(
            myDayId, command.userId(), command.localDate(), command.timezone(), periodCode, language,
            command.orchestrationId(), command.orchestrationVersion(), command.baseline().share(),
            provenance(command), generated.cardSource(), generated.generationPolicyVersion(),
            generated.promptVersion(), generated.modelRef(), generated.safetyVersion(), generated.cacheKey(), now);
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
            "generationPolicyVersion", generated.generationPolicyVersion()
        )));
        return new Result(myDayId, "CREATED", command.orchestrationId());
    }

    private static MyDayException notReady() {
        return new MyDayException("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", "My Day is not ready yet");
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
