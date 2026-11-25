package com.momna.modules.myday;

import static org.junit.jupiter.api.Assertions.*;

import com.momna.modules.checkin.application.CheckinTexts;
import com.momna.modules.checkin.domain.CheckinPhase;
import com.momna.modules.myday.application.MyDayTexts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The texts of the My Day and Check-in screens: Adult Cycle as in the implemented design (Figma export of
 * 24.11.2026); every other period written by the backend on the same pattern (Product decision 25.11.2026).
 */
class MyDayTextsTest {
    private static final List<String> PERIODS = List.of("MENARCHE", "CYCLE", "PLANNING", "PREGNANCY", "POSTPARTUM",
        "PERIMENOPAUSE", "MENOPAUSE");

    @Test
    void adultCycleFollowsTheDesign() {
        var en = MyDayTexts.of("en");
        assertEquals("Recommendations for a regular cycle.", en.subtitle("CYCLE"));
        assertEquals("General recommendations for a regular cycle — no questions.", en.general("CYCLE").description());
        assertEquals("Standard", en.general("CYCLE").title());
        assertEquals("Getting your plan ready", en.generating().title());
        assertEquals("I'm turning your answers into today's recommendations.", en.generating().description());
        assertEquals("TODAY'S PLAN", en.planHeader());
        assertEquals(Map.of("AM", "Morning", "PM", "Evening", "OPTIONAL", "Optional"), en.groupTitles());
    }

    /** No period is left without its texts, in either language, and no period borrows another one's. */
    @Test
    void everyPeriodHasItsOwnTextsInBothLanguages() {
        for (var language : List.of("en", "ru")) {
            var texts = MyDayTexts.of(language);
            assertEquals(PERIODS.size(), PERIODS.stream().map(texts::subtitle).distinct().count(), language);
            for (var period : PERIODS) {
                assertFalse(texts.subtitle(period) == null || texts.subtitle(period).isBlank(), language + " " + period);
                assertTrue(texts.general(period).description().endsWith("no questions.")
                    || texts.general(period).description().endsWith("без вопросов."), language + " " + period);
                assertNotEquals(texts.general().description(), texts.general(period).description(), period);
            }
            assertEquals(MyDayTexts.of(language).groupTitles().keySet(), java.util.Set.of("AM", "PM", "OPTIONAL"));
            assertFalse(texts.planHeader().isBlank());
            assertFalse(texts.emptyPlanText().isBlank());
        }
        assertEquals("Рекомендации на время беременности.", MyDayTexts.of("ru").subtitle("PREGNANCY"));
        assertEquals("Recommendations for your first year after birth.", MyDayTexts.of("en").subtitle("POSTPARTUM"));
    }

    @Test
    void checkinTabCardsAndSheetsFollowTheDesign() {
        assertEquals("How you feel, turned into a plan.", CheckinTexts.subtitle("en"));
        assertEquals("How are you today?", CheckinTexts.invite(CheckinPhase.MORNING, "en").title());
        assertEquals("How did today go?", CheckinTexts.invite(CheckinPhase.EVENING, "en").title());
        assertEquals("Note how the day went — it shapes tomorrow's plan.", CheckinTexts.invite(CheckinPhase.EVENING, "en").body());
        assertNull(CheckinTexts.invite(CheckinPhase.MORNING, "en").statusLabel());
        assertEquals("Your personal plan is ready", CheckinTexts.completed(CheckinPhase.MORNING, "en").title());
        assertEquals("Completed", CheckinTexts.completed(CheckinPhase.MORNING, "en").statusLabel());
        assertNull(CheckinTexts.completed(CheckinPhase.EVENING, "en").title());
        assertEquals("Your plan for tomorrow is on its way — see you in the morning.",
            CheckinTexts.completed(CheckinPhase.EVENING, "en").body());
        assertTrue(CheckinTexts.invite(CheckinPhase.MORNING, "ru-RU").title().startsWith("Как"));
        assertEquals(new CheckinTexts.Sheet("Morning Check-in", "Tell me how you feel — I'll handle today."),
            CheckinTexts.sheet(CheckinPhase.MORNING, "en"));
        assertEquals("Вечерний чек-ин", CheckinTexts.sheet(CheckinPhase.EVENING, "ru").title());
        assertEquals(1, CheckinTexts.MIN_ANSWERS_TO_APPLY);
    }
}
