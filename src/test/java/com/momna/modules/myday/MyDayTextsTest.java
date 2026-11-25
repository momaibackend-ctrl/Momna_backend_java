package com.momna.modules.myday;

import static org.junit.jupiter.api.Assertions.*;

import com.momna.modules.checkin.application.CheckinTexts;
import com.momna.modules.checkin.domain.CheckinPhase;
import com.momna.modules.myday.application.MyDayTexts;
import org.junit.jupiter.api.Test;

/** Texts of the implemented design (Figma export of 24.11.2026, Adult Cycle). */
class MyDayTextsTest {
    @Test
    void periodTextsFollowTheDesignAndAreNeverInventedForUndesignedPeriods() {
        var en = MyDayTexts.of("en");
        assertEquals("Recommendations for a regular cycle.", en.subtitle("CYCLE"));
        assertEquals("General recommendations for a regular cycle — no questions.", en.general("CYCLE").description());
        assertEquals("Standard", en.general("CYCLE").title());
        // No design export yet for the other periods: no subtitle, the neutral Standard text.
        for (var period : java.util.List.of("MENARCHE", "PLANNING", "PREGNANCY", "POSTPARTUM", "PERIMENOPAUSE", "MENOPAUSE")) {
            assertNull(en.subtitle(period), period);
            assertEquals("General recommendations — no questions.", en.general(period).description(), period);
        }
        assertEquals("Getting your plan ready", en.generating().title());
        assertEquals("I'm turning your answers into today's recommendations.", en.generating().description());
        assertNotNull(MyDayTexts.of("ru").subtitle("CYCLE"));
    }

    @Test
    void checkinTabCardsFollowTheDesign() {
        assertEquals("How you feel, turned into a plan.", CheckinTexts.subtitle("en"));
        assertEquals("How are you today?", CheckinTexts.invite(CheckinPhase.MORNING, "en").title());
        assertEquals("How did today go?", CheckinTexts.invite(CheckinPhase.EVENING, "en").title());
        assertEquals("Note how the day went — it shapes tomorrow's plan.", CheckinTexts.invite(CheckinPhase.EVENING, "en").body());
        assertEquals("Your personal plan is ready", CheckinTexts.completed(CheckinPhase.MORNING, "en").title());
        assertNull(CheckinTexts.completed(CheckinPhase.EVENING, "en").title());
        assertEquals("Your plan for tomorrow is on its way — see you in the morning.",
            CheckinTexts.completed(CheckinPhase.EVENING, "en").body());
        assertTrue(CheckinTexts.invite(CheckinPhase.MORNING, "ru-RU").title().startsWith("Как"));
    }
}
