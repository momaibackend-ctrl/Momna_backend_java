package com.momna.modules.checkin.safety;

import com.momna.modules.checkin.application.CheckinException;
import com.momna.modules.checkin.domain.CheckinPhase;
import com.momna.modules.checkin.domain.CheckinSafetyState;
import com.momna.modules.checkin.infrastructure.CheckinSessionEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenarcheSafetyDecisionServiceTest {
    private CheckinSessionEntity blocked() {
        var now=Instant.parse("2026-10-11T12:00:00Z");
        var session=new CheckinSessionEntity(
            "session-1","user-1",CheckinPhase.MORNING,LocalDate.of(2026,10,11),
            "MENARCHE",null,"UTC",3,"weighted-v3",List.of("M4"),
            now.minusSeconds(3600),now.plusSeconds(3600),now);
        session.applySafetyDecision(CheckinSafetyState.CLARIFICATION_REQUIRED,
            "safety.clinical.v1:safety.catalog.v1:MENARCHE_DIZZINESS",
            "MENARCHE_DIZZINESS","HIGH",true,
            Map.of("id","clarification-1","presentationKey","checkin.safety.menarche_dizziness"),
            List.of("CONTACT_CLINICIAN"),now);
        return session;
    }

    @Test void urgentResponseKeepsBlockingAndEmergencyAction() {
        var session=blocked();
        var service=new MenarcheSafetyDecisionService();
        service.answer(session,"clarification-1","severe",Instant.now());
        assertTrue(session.isSafetyBlocking());
        assertEquals("CRITICAL",session.getSafetySeverity());
        assertEquals("CLARIFICATION_REQUIRED",session.getSafetyState().name());
        assertTrue(session.getSafetyAllowedActions().contains("CONTACT_EMERGENCY_SERVICES"));
        assertThrows(CheckinException.class,
            () -> service.answer(session,"clarification-1","not_severe",Instant.now()));
    }

    @Test void nonSevereResponseRetainsClinicianGuidance() {
        var session=blocked();
        new MenarcheSafetyDecisionService().answer(
            session,"clarification-1","not_severe",Instant.now());
        assertFalse(session.isSafetyBlocking());
        assertEquals("RESOLVED",session.getSafetyState().name());
        assertTrue(session.getSafetyAllowedActions().contains("CONTACT_CLINICIAN"));
    }

    @Test void unknownOptionDoesNotAlterSafetyState() {
        var session=blocked();
        var ex=assertThrows(CheckinException.class,
            () -> new MenarcheSafetyDecisionService().answer(
                session,"clarification-1","skip",Instant.now()));
        assertEquals("SAFETY_VALUE_INVALID",ex.code());
        assertTrue(session.isSafetyBlocking());
    }
}
