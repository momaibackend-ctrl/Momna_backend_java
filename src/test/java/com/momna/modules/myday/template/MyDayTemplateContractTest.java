package com.momna.modules.myday.template;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.momna.modules.myday.application.template.MyDayCalendarFacts;
import com.momna.modules.myday.application.template.MyDayOverlayAdapter;
import com.momna.modules.myday.application.template.MyDayTemplateKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * contracts/myday-template-key-overlays.json is the reviewed table of MOMNA-1324/1325. It is generated
 * from the code, and this test fails when they differ. Regenerate with MYDAY_CONTRACT_WRITE=true.
 */
class MyDayTemplateContractTest {
    private static final Path FILE = Path.of("contracts/myday-template-key-overlays.json");

    @Test
    void theContractFileIsTheCode() throws Exception {
        var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var expected = mapper.writeValueAsString(contract()).replace("\r\n", "\n") + "\n";
        if ("true".equals(System.getenv("MYDAY_CONTRACT_WRITE"))) {
            Files.writeString(FILE, expected, StandardCharsets.UTF_8);
        }
        assertEquals(expected, Files.readString(FILE, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    static Map<String, Object> contract() {
        var out = new LinkedHashMap<String, Object>();
        out.put("contract", "myday-template-key-overlays");
        out.put("tasks", List.of("MOMNA-1324", "MOMNA-1325", "MOMNA-989"));
        out.put("approvedBy", "Product (vkdevpro), 23.11.2026 and 25.11.2026; medical keys: Safety Platform (MOMNA-1145)");
        var key = new LinkedHashMap<String, Object>();
        key.put("keyVersion", MyDayTemplateKey.KEY_VERSION);
        key.put("purpose", MyDayTemplateKey.PURPOSE);
        key.put("components", List.of("life_mode", "day_number", "cycle_state", "phase_code", "week_number",
            "trimester", "overlay_keys (sorted)", "user_language", "tone (gentle | friendly | close_friend | direct | null)",
            "prompts version", "validator version",
            "overlay-adapter version", "calendar-facts and phase-code version", "content-rules version"));
        var v = MyDayTemplateKey.Versions.current();
        var versions = new LinkedHashMap<String, Object>();
        versions.put("prompts", v.prompts());
        versions.put("validator", v.validator());
        versions.put("overlayAdapter", v.overlayAdapter());
        versions.put("calendarFacts", v.calendarFacts());
        versions.put("contentRules", v.contentRules());
        key.put("currentVersions", versions);
        key.put("fingerprint", "SHA-256 of the canonical form; never contains a user id, session id, answer or text");
        key.put("cacheTtlDaysMax", 90);
        out.put("templateKey", key);
        var calendar = new LinkedHashMap<String, Object>();
        calendar.put("version", MyDayCalendarFacts.VERSION);
        calendar.put("phaseCodeVersion", MyDayCalendarFacts.PHASE_CODE_VERSION);
        calendar.put("cycleState", List.of(
            "cycle day = days since the onboarding LMP + 1; no LMP or a future LMP: uncertain",
            "days 1-5: menstrual",
            "regular cycle only (adult_cycle around_expected, planning fairly_predictable), Standard Days Method: "
                + "6-7 follicular, 8-19 fertile_window, 20-32 luteal, later uncertain",
            "irregular, menarche, perimenopause after day 5: uncertain; ovulation_peak is never derived",
            "pregnancy, postpartum, menopause: not_applicable"));
        calendar.put("pregnancyWeek", "completed weeks (ACOG) from the due date (280 days), else LMP, else the "
            + "reported week plus the days since it was given in the user's zone; valid to 44 weeks, reported 1-42");
        calendar.put("trimester", "1: to 13 6/7 weeks; 2: 14 0/7 to 27 6/7; 3: from 28 0/7 (ACOG)");
        calendar.put("postpartumWeek", "week 1 = days 0-6 after the birth date; valid to week 52");
        calendar.put("dayNumber", "day in the current life mode; day 1 = the day it started");
        calendar.put("phaseCode", List.of("teen_<cycle_state>", "adult_<cycle_state>", "planning_<cycle_state>",
            "preg_trimester<n>_w<week> | preg_unknown", "post_w<week> | post_unknown", "peri_<cycle_state>",
            "meno_general"));
        out.put("calendarFacts", calendar);
        var adapter = new LinkedHashMap<String, Object>();
        adapter.put("version", MyDayOverlayAdapter.VERSION);
        adapter.put("checkinActivation", "strength = sum over phases that answered one of the key's items of "
            + "(strongest state: 0, 0.5 middle, 1 strongest) x phase base share, divided by the sum of those shares; "
            + "on at >= " + MyDayOverlayAdapter.ACTIVATION_PERCENT + "%");
        adapter.put("phaseShares", "Morning(D) 0.7 + Evening(D-1) 0.3 when both are final sources; 1.0 for the only one");
        adapter.put("medical", "only from the SafetyDecision of a final source session, severity MEDIUM/HIGH/CRITICAL "
            + "or UNKNOWN, on the period's own routes or a GLOBAL route; one <period>_health_flag per period, R18 "
            + "POSTPARTUM_MENTAL_HEALTH is post_mood_heavy; never from a score, a profile answer or the LLM");
        adapter.put("healthNote", "only a day with a passed medical key gets a doctor line: the strongest decision "
            + "behind it gives the level (CRITICAL EMERGENCY, HIGH URGENT, MEDIUM PROMPT, UNKNOWN); PROMPT and UNKNOWN "
            + "add the code-owned recommendation, URGENT and EMERGENCY none (the urgent path shows them); every such "
            + "day adds the disclaimer; texts " + com.momna.modules.myday.application.MyDayHealthTexts.VERSION);
        adapter.put("tone", "onboarding.<period>.tone, one of the four tones, else null");
        var caps = new LinkedHashMap<String, Object>();
        caps.put("checkinKeys", MyDayOverlayAdapter.MAX_STATE_KEYS);
        caps.put("factKeys", MyDayOverlayAdapter.MAX_FACT_KEYS);
        caps.put("medicalKeys", "all");
        adapter.put("dailyCaps", caps);
        adapter.put("computedOnce", "when the day is generated; later Check-ins do not change today's keys");
        adapter.put("withoutCheckin", "only onboarding and calendar keys");
        var entries = new ArrayList<Map<String, Object>>();
        for (var e : MyDayOverlayAdapter.CATALOG) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("overlayKey", e.key());
            entry.put("lifeMode", e.lifeMode());
            entry.put("sourceType", e.sourceType().name());
            entry.put("cap", e.group().name());
            entry.put("rule", e.rule().toString());
            entry.put("meaning", e.meaning());
            entry.put("owner", e.owner());
            entries.add(entry);
        }
        adapter.put("entries", entries);
        out.put("overlayAdapter", adapter);
        return out;
    }
}
