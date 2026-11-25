package com.momna.modules.myday.application;

import com.momna.modules.lifecycle.application.LifecycleQueryService;
import com.momna.modules.lifecycle.domain.LifecycleEntry;
import com.momna.modules.myday.application.template.*;
import com.momna.modules.myday.infrastructure.*;
import com.momna.modules.profile.application.ProfileService;
import com.momna.shared.outbox.OutboxEventEntity;
import com.momna.shared.outbox.OutboxEventRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The durable side of My Day: one personal day per (user, local date), generated once. A new day
 * takes its cards from the configured {@link MyDayCardSource}; its sources are fixed at that moment
 * and nothing later changes them (MOMNA-989, 23.11.2026). Called by the Check-in orchestrator, which
 * consumes the source sessions after this returns.
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
    private final ObjectProvider<MyDayOnboardingFactsPort> onboardingFacts;
    private final Clock clock = Clock.systemUTC();

    public MyDayMaterializationService(
        MyDayRepository days,
        MyDayCardRepository cards,
        OutboxEventRepository outbox,
        LifecycleQueryService lifecycle,
        ProfileService profiles,
        ObjectProvider<MyDayCardSource> cardSources,
        ObjectProvider<MyDayOnboardingFactsPort> onboardingFacts
    ) {
        this.days = days;
        this.cards = cards;
        this.outbox = outbox;
        this.lifecycle = lifecycle;
        this.profiles = profiles;
        this.cardSources = cardSources;
        this.onboardingFacts = onboardingFacts;
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
            // One generation per day: the stored day is returned as it is, with its own sources.
            return new Result(existing.getMyDayId(), "EXISTING", existing.getOrchestrationId());
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
        Template template;
        try {
            template = template(command, period, lifeMode, language);
            var source = cardSources.getIfAvailable();
            if (source == null) throw notReady();
            generated = source.generate(new MyDayCardSource.Input(
                command.userId(), command.localDate(), command.timezone(), periodCode, lifeMode, language,
                command.orchestrationId(), command.baseline().share(), command.sources(), command.adjustments(),
                template.key(), template.overlays().activations()));
            MyDayCards.requireComplete(generated.cards());
        } catch (RuntimeException failure) {
            throw notReady();
        }

        var myDayId = UUID.randomUUID().toString();
        var day = new MyDayEntity(
            myDayId, command.userId(), command.localDate(), command.timezone(), periodCode, language,
            command.orchestrationId(), command.orchestrationVersion(), command.baseline().share(),
            provenance(command, template), generated.cardSource(), generated.generationPolicyVersion(),
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

    @Override
    public java.util.Optional<String> findMyDayId(String userId, LocalDate localDate) {
        return days.findByUserIdAndLocalDate(userId, localDate).map(MyDayEntity::getMyDayId);
    }

    private static MyDayException notReady() {
        return new MyDayException("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", "My Day is not ready yet");
    }

    public static String cardId(String slot, int position) {
        return slot.toLowerCase(Locale.ROOT) + "-" + position;
    }

    record Template(MyDayTemplateKey key, MyDayOverlayAdapter.Result overlays) {}

    /**
     * The anonymous prompt inputs (MOMNA-1325) and the overlay keys (MOMNA-1324), computed once, when the
     * day is generated. Onboarding answers are read through the onboarding module's contract.
     */
    Template template(Command command, LifecycleEntry period, String lifeMode, String language) {
        var zone = ZoneId.of(command.timezone());
        var facts = onboardingFacts.getIfAvailable();
        Map<String, MyDayFacts.OnboardingFact> onboarding = facts == null ? Map.of()
            : facts.facts(command.userId(), period.period().name(), MyDayOverlayAdapter.ONBOARDING_FIELDS);
        var start = period.effectiveFrom() == null ? null : period.effectiveFrom().atZone(zone).toLocalDate();
        var calendar = MyDayCalendarFacts.derive(lifeMode, onboarding, command.localDate(), start, zone);
        var overlays = MyDayOverlayAdapter.compute(new MyDayOverlayAdapter.Input(lifeMode, command.localDate(),
            command.observations(), command.safety(), onboarding, calendar));
        var key = new MyDayTemplateKey(lifeMode, calendar.dayNumber(), calendar.cycleState(), calendar.phaseCode(),
            calendar.weekNumber(), calendar.trimester(), overlays.overlayKeys(), language,
            MyDayOverlayAdapter.tone(lifeMode, onboarding), MyDayTemplateKey.Versions.current());
        return new Template(key, overlays);
    }

    static Map<String, Object> provenance(Command command) {
        return provenance(command, null);
    }

    /** Evening(D-1) and Morning(D) both target My Day(D); provenance keeps both dates explicit. */
    static Map<String, Object> provenance(Command command, Template template) {
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
        if (template != null) {
            var key = new LinkedHashMap<String, Object>();
            key.put("keyVersion", MyDayTemplateKey.KEY_VERSION);
            key.put("fingerprint", template.key().fingerprint());
            key.put("parameters", new LinkedHashMap<>(template.key().promptParameters()));
            key.put("versions", Map.of(
                "prompts", template.key().versions().prompts(),
                "validator", template.key().versions().validator(),
                "overlayAdapter", template.key().versions().overlayAdapter(),
                "calendarFacts", template.key().versions().calendarFacts(),
                "contentRules", template.key().versions().contentRules()));
            var activations = new ArrayList<Map<String, Object>>();
            for (var a : template.overlays().activations()) {
                activations.add(Map.of("key", a.key(), "group", a.group().name(),
                    "strength", a.strength().toPlainString(), "passed", a.passed()));
            }
            key.put("overlayActivations", activations);
            // The doctor recommendation level of a day with a medical overlay; absent on every other day.
            if (template.overlays().healthLevel() != null) {
                key.put("healthLevel", template.overlays().healthLevel().name());
                key.put("healthTexts", MyDayHealthTexts.VERSION);
            }
            result.put("template", key);
        }
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
