package com.momna.modules.checkin.application;

import com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The question and answer texts of a Check-in item, and the tone each answer takes when selected (the app's
 * {@code Question} and {@code AnswerOption}, plan.md D-2 and D-10). Texts come from the governed weighted model
 * in the user's language, in Sentence case (plan.md D-3).
 *
 * <p>Tones: by default the first answer is GOOD, the second NOT_GOOD, the third BAD, as in the confirmed Adult
 * Cycle questions. Items whose scale is not "better to worse" have their own tones:
 * <ul>
 *   <li>a load scale (light, ordinary, packed): GOOD, NEUTRAL, NOT_GOOD (D-10, "Day's workload");</li>
 *   <li>a looking-back scale (easier or improved, as usual, harder or worse): GOOD, NEUTRAL, BAD;</li>
 *   <li>a preference or a plan for the day: NEUTRAL throughout, or NEUTRAL, NEUTRAL, NOT_GOOD when its last
 *       answer is a demanding day;</li>
 *   <li>postpartum "fine, hard, can't tell yet": GOOD, BAD, NEUTRAL.</li>
 * </ul>
 * Written by the backend after D-10 on 25.11.2026 and approved by Product on 26.11.2026.
 */
public final class CheckinItemTexts {
    public static final String GOOD = "GOOD";
    public static final String NOT_GOOD = "NOT_GOOD";
    public static final String BAD = "BAD";
    public static final String NEUTRAL = "NEUTRAL";

    static final List<String> DEFAULT = List.of(GOOD, NOT_GOOD, BAD);
    private static final List<String> LOAD = List.of(GOOD, NEUTRAL, NOT_GOOD);
    private static final List<String> LOOKING_BACK = List.of(GOOD, NEUTRAL, BAD);
    private static final List<String> PREFERENCE = List.of(NEUTRAL, NEUTRAL, NEUTRAL);
    private static final List<String> PLAN = List.of(NEUTRAL, NEUTRAL, NOT_GOOD);

    static final Map<String, List<String>> TONES = Map.ofEntries(
        Map.entry("C8", LOAD), Map.entry("PM6", LOAD), Map.entry("MN10", LOAD), Map.entry("M6", LOAD),
        Map.entry("MN9", LOAD),
        Map.entry("M9", LOOKING_BACK), Map.entry("C10", LOOKING_BACK), Map.entry("P13", LOOKING_BACK),
        Map.entry("MN11", LOOKING_BACK),
        Map.entry("PM7", List.of(NEUTRAL, GOOD, BAD)),
        Map.entry("T2", PREFERENCE), Map.entry("T3", PREFERENCE), Map.entry("P11", PREFERENCE),
        Map.entry("T4", PLAN), Map.entry("P10", PLAN), Map.entry("P17", PLAN),
        Map.entry("PP1", List.of(GOOD, BAD, NEUTRAL)), Map.entry("PP2", List.of(GOOD, BAD, NEUTRAL))
    );

    private CheckinItemTexts() {}

    public record Option(int value, String text, String tone) {}

    /** The items whose tones are not the default. */
    public static java.util.Set<String> itemsWithOwnTones() {
        return TONES.keySet();
    }

    public static String title(WeightedCheckinDefinitionCatalog.Item item, String language) {
        return sentence(ru(language) ? item.labelRu() : item.labelEnUs());
    }

    public static List<Option> options(WeightedCheckinDefinitionCatalog.Item item, String language) {
        var tones = TONES.getOrDefault(item.itemCode(), DEFAULT);
        var out = new ArrayList<Option>();
        for (var state : item.states()) {
            out.add(new Option(state.value(), sentence(ru(language) ? state.ru() : state.enUs()),
                state.value() < tones.size() ? tones.get(state.value()) : NEUTRAL));
        }
        return List.copyOf(out);
    }

    static String sentence(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private static boolean ru(String language) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("ru");
    }
}
