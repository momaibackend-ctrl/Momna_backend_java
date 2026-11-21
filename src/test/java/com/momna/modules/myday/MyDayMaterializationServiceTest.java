package com.momna.modules.myday;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.momna.modules.lifecycle.application.LifecycleQueryService;
import com.momna.modules.lifecycle.domain.LifecycleEntry;
import com.momna.modules.lifecycle.domain.LifecyclePeriod;
import com.momna.modules.lifecycle.domain.LifecycleSnapshot;
import com.momna.modules.myday.application.MyDayCardSource;
import com.momna.modules.myday.application.MyDayException;
import com.momna.modules.myday.application.MyDayMaterializationPort.*;
import com.momna.modules.myday.application.MyDayMaterializationService;
import com.momna.modules.myday.infrastructure.*;
import com.momna.modules.profile.application.ProfileService;
import com.momna.shared.outbox.OutboxEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MyDayMaterializationServiceTest {
    private static final LocalDate DAY = LocalDate.parse("2026-11-21");
    private final MyDayRepository days = mock(MyDayRepository.class);
    private final MyDayCardRepository cards = mock(MyDayCardRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MyDayCardSource> sources = mock(ObjectProvider.class);
    private final MyDayCardSource source = mock(MyDayCardSource.class);
    private final MyDayGenerationCacheRepository cache = mock(MyDayGenerationCacheRepository.class);
    private final LifecycleQueryService lifecycle = lifecycle();
    private final MyDayMaterializationService service = new MyDayMaterializationService(
        days, cards, outbox, lifecycle, mock(ProfileService.class), sources, cache);

    private static LifecycleQueryService lifecycle() {
        var lifecycle = mock(LifecycleQueryService.class);
        var entry = mock(LifecycleEntry.class);
        when(entry.period()).thenReturn(LifecyclePeriod.CYCLE);
        when(lifecycle.current(any(), any())).thenReturn(new LifecycleSnapshot(entry, List.of()));
        return lifecycle;
    }

    @Test
    void withoutACardSourceOrCachedDayNothingIsStoredAndGenerationFailsClosed() {
        when(days.findByUserIdAndLocalDate("u1", DAY)).thenReturn(Optional.empty());
        when(sources.getIfAvailable()).thenReturn(null);
        when(cache.findFirstByLifeModeAndLanguageAndExpiresAtAfterOrderByCreatedAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());
        var failure = assertThrows(MyDayException.class, () -> service.upsert(command(null, "orch-1")));
        assertEquals("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", failure.code());
        verifyNoInteractions(cards, outbox);
        verify(days, never()).saveAndFlush(any());
    }

    @Test
    void anInvalidAnswerIsNeverStoredAndASimilarVerifiedDayIsServedDegraded() {
        when(days.findByUserIdAndLocalDate("u1", DAY)).thenReturn(Optional.empty());
        when(sources.getIfAvailable()).thenReturn(source);
        when(source.generate(any())).thenReturn(new com.momna.modules.myday.application.MyDayCards.GeneratedDay(
            List.of(), "AI", "myday.generation.v1", null, null, null, "key-invalid"));
        var cached = new MyDayGenerationCacheEntity("key-1", "adult_cycle", "en", nineCards(), "AI",
            "myday.generation.v1", "p1", "m1", "s1", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        when(cache.findFirstByLifeModeAndLanguageAndExpiresAtAfterOrderByCreatedAtDesc(eq("adult_cycle"), eq("en"), any()))
            .thenReturn(Optional.of(cached));
        var result = service.upsert(command(null, "orch-1"));
        assertEquals("CREATED", result.outcome());
        var saved = org.mockito.ArgumentCaptor.forClass(MyDayEntity.class);
        verify(days).saveAndFlush(saved.capture());
        assertEquals("DEGRADED_READY", saved.getValue().getStatus());
        assertEquals("CACHE_FALLBACK", saved.getValue().getCardSource());
        assertEquals("key-1", saved.getValue().getCacheKey());
        verify(cards, times(9)).save(any());
        verify(cache, never()).save(any());
    }

    private static List<Map<String, Object>> nineCards() {
        var result = new java.util.ArrayList<Map<String, Object>>();
        for (var slot : List.of("AM", "PM", "OPTIONAL")) {
            for (int i = 1; i <= 3; i++) {
                result.add(Map.of("slot", slot, "position", i, "title", "t", "microcopy", "m", "whatToDo", "w",
                    "whyToday", "y", "howToDo", "h", "ifSkipped", "i", "support", "s"));
            }
        }
        return result;
    }

    @Test
    void sameOrchestrationIsUnchangedAndNeverRegenerates() {
        when(days.findByUserIdAndLocalDate("u1", DAY)).thenReturn(Optional.of(existing("orch-1")));
        var result = service.upsert(command(null, "orch-1"));
        assertEquals("UNCHANGED", result.outcome());
        assertEquals("day-1", result.myDayId());
        verifyNoInteractions(sources, source, cards, outbox, cache);
    }

    @Test
    void laterCheckinUpdatesProvenanceOnlyAndKeepsCardsAndProgress() {
        var day = existing("orch-1");
        when(days.findByUserIdAndLocalDate("u1", DAY)).thenReturn(Optional.of(day));
        var result = service.upsert(command("day-1", "orch-2"));
        assertEquals("PROVENANCE_UPDATED", result.outcome());
        assertEquals("orch-2", day.getOrchestrationId());
        assertEquals("2026-11-20", ((List<Map<String, Object>>) day.getProvenance().get("sources")).stream()
            .filter(s -> "EVENING".equals(s.get("phase"))).findFirst().orElseThrow().get("sourceLocalDate"));
        verifyNoInteractions(sources, source, cards, outbox);
    }

    @Test
    void aTargetThatIsNotThisDaysMyDayFailsClosed() {
        when(days.findByUserIdAndLocalDate("u1", DAY)).thenReturn(Optional.of(existing("orch-1")));
        assertEquals("MY_DAY_TARGET_CONFLICT",
            assertThrows(MyDayException.class, () -> service.upsert(command("day-other", "orch-2"))).code());
        when(days.findByUserIdAndLocalDate("u1", DAY)).thenReturn(Optional.empty());
        assertEquals("MY_DAY_TARGET_CONFLICT",
            assertThrows(MyDayException.class, () -> service.upsert(command("day-1", "orch-2"))).code());
    }

    private MyDayEntity existing(String orchestrationId) {
        return new MyDayEntity("day-1", "u1", DAY, "UTC", "CYCLE", "en", orchestrationId, "v2",
            BigDecimal.ONE, Map.of(), "test", "myday.generation.v1", null, null, null, null, Instant.now());
    }

    private Command command(String target, String orchestrationId) {
        return new Command("u1", DAY, "UTC", target, orchestrationId, "checkin-myday-orchestration.v2", orchestrationId,
            new BaselineReference("c", "p", "PERIOD_FALLBACK_REQUIRED", "b", 1, 1, 1, new BigDecimal("0.300000")),
            List.of(
                new SourceReference("CHECKIN", "s-m", "MORNING", "SUBMITTED", "FRESH", BigDecimal.ONE,
                    new BigDecimal("0.700000"), new BigDecimal("0.700000"), "3", "weighted-v3"),
                new SourceReference("CHECKIN", "s-e", "EVENING", "SUBMITTED", "FRESH", BigDecimal.ZERO,
                    new BigDecimal("0.300000"), new BigDecimal("0.000000"), "3", "weighted-v3")),
            List.of());
    }
}
