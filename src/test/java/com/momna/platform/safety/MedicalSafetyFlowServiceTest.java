package com.momna.platform.safety;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.application.CheckinViews.AnswerChange;
import com.momna.modules.checkin.infrastructure.CheckinSessionEntity;
import com.momna.modules.checkin.scoring.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class MedicalSafetyFlowServiceTest {
    private final MedicalSafetyRouteStateRepository repo =
        mock(MedicalSafetyRouteStateRepository.class);
    private final MedicalSafetyCatalog catalog = new MedicalSafetyCatalog(new ObjectMapper());
    private final MedicalSafetyFlowService service = new MedicalSafetyFlowService(
        catalog, new CanonicalSafetyService(), repo,
        mock(MedicalSafetyWb1ResponseRepository.class),
        mock(MedicalSafetyFactRevisionRepository.class)
    );

    private CheckinSessionEntity session(String period) {
        var session = mock(CheckinSessionEntity.class);
        when(session.getUserId()).thenReturn("person-1");
        when(session.getSessionId()).thenReturn("session-1");
        when(session.getPeriodAtTime()).thenReturn(period);
        return session;
    }

    private CheckinScoringResult scored(boolean cumulative, List<CheckinScoringResult.Contribution> items) {
        return new CheckinScoringResult(BigDecimal.ZERO,
            cumulative ? BigDecimal.valueOf(14) : BigDecimal.ZERO,
            CheckinAdjustmentTier.NONE, cumulative, items.size(), items.size(), items);
    }

    @Test
    void directEntryP19StartsRouteEvenWithZeroSafetyScore() {
        when(repo.findByUserIdAndRouteId("person-1","R10"))
            .thenReturn(Optional.empty());
        when(repo.findByUserId("person-1")).thenReturn(List.of());
        var result = service.onCheckinPatch(session("PREGNANCY"),
            List.of(new AnswerChange("P19", 2)), Map.of("P19", 2),
            scored(false, List.of()));
        assertTrue(result.isEmpty()); // repository mocked; step can be retrieved after persistence
        var captor = org.mockito.ArgumentCaptor.forClass(MedicalSafetyRouteStateEntity.class);
        verify(repo).saveAndFlush(captor.capture());
        assertEquals("R10", captor.getValue().getRouteId());
        assertEquals("QUESTION", captor.getValue().getState());
    }

    @Test
    void pp2StateTwoNeverTriggersMentalHealthViaCumulativeFallback() {
        when(repo.findByUserId("person-1")).thenReturn(List.of());
        var zero = BigDecimal.ZERO;
        var contribution = new CheckinScoringResult.Contribution(
            "PP2",2, zero,zero,zero, BigDecimal.valueOf(7),
            BigDecimal.valueOf(7), BigDecimal.valueOf(14),List.of());
        service.onCheckinPatch(session("POSTPARTUM"),
            List.of(new AnswerChange("PP2",2)), Map.of("PP2",2),
            scored(true, List.of(contribution)));
        verify(repo, never()).saveAndFlush(any(MedicalSafetyRouteStateEntity.class));
    }

    @Test
    void pp2StateOneEntersR18WithoutNumericSafetyWeight() {
        when(repo.findByUserIdAndRouteId("person-1","R18")).thenReturn(Optional.empty());
        when(repo.findByUserId("person-1")).thenReturn(List.of());
        service.onCheckinPatch(session("POSTPARTUM"),
            List.of(new AnswerChange("PP2",1)), Map.of("PP2",1),
            scored(false, List.of()));
        var captor = org.mockito.ArgumentCaptor.forClass(MedicalSafetyRouteStateEntity.class);
        verify(repo).saveAndFlush(captor.capture());
        assertEquals("R18", captor.getValue().getRouteId());
    }
    @Test
    void qmSelectedWritesAllFiveFactsInOneAtomicStep() {
        var at = java.time.Instant.parse("2026-10-20T09:00:00Z");
        var record = new MedicalSafetyRouteStateEntity("person-1", "R16", at);
        record.begin("session-1", "PP14", at);
        for (var code : new String[] {"F_BREATHING_SEVERE", "F_CHEST_PAIN",
            "F_FAINTED", "F_SEIZURE", "F_HARM_THOUGHTS"}) {
            record.answer(code, "NO", at);
        }
        when(repo.findByUserIdAndRouteId("person-1", "R16"))
            .thenReturn(Optional.of(record));
        when(repo.findByUserId("person-1")).thenReturn(List.of(record));
        var out = service.answerChecklist("person-1", "session-1",
            "R16", "SELECTED", List.of("F_HEART_RACING"));
        assertEquals(5, catalog.systemicChecklistFacts().size());
        assertEquals("YES", record.getFactAnswers().get("F_HEART_RACING"));
        for (var code : catalog.systemicChecklistFacts()) {
            if (!code.equals("F_HEART_RACING"))
                assertEquals("NO", record.getFactAnswers().get(code), code);
        }
        assertEquals("COMPLETE", record.getState());
        assertEquals("URGENT", out.getFirst().disposition());
        verify(repo).saveAndFlush(record);
    }

    @Test
    void qmNoneUnsureAndSkipAreExplicitAndRejectInapplicableSelections() {
        for (var mode : new String[] {"NONE", "UNSURE", "SKIP"}) {
            reset(repo);
            var at = java.time.Instant.parse("2026-10-20T09:00:00Z");
            var record = new MedicalSafetyRouteStateEntity("person-1", "R16", at);
            record.begin("session-1", "PP14", at);
            for (var code : new String[] {"F_BREATHING_SEVERE", "F_CHEST_PAIN",
                "F_FAINTED", "F_SEIZURE", "F_HARM_THOUGHTS"}) {
                record.answer(code, "NO", at);
            }
            when(repo.findByUserIdAndRouteId("person-1", "R16"))
                .thenReturn(Optional.of(record));
            when(repo.findByUserId("person-1")).thenReturn(List.of(record));
            assertThrows(IllegalArgumentException.class,
                () -> service.answerChecklist("person-1","session-1", "R16",
                    mode, List.of("F_CHEST_PAIN")));
            service.answerChecklist("person-1", "session-1", "R16", mode, List.of());
            var expected = mode.equals("NONE") ? "NO"
                : mode.equals("UNSURE") ? "UNSURE" : "ABSENT";
            for (var code : catalog.systemicChecklistFacts())
                assertEquals(expected, record.getFactAnswers().get(code));
        }
    }

}
