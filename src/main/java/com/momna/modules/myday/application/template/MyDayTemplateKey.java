package com.momna.modules.myday.application.template;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/**
 * MYDAY-BE-02 (MOMNA-1325): every input of the My Day prompts v1.1, and nothing else. It is the only
 * thing that reaches the prompt and the template-cache fingerprint, so it is anonymous by construction:
 * every field is a closed vocabulary or a bounded number, validated here. A user id, a name, a session
 * id, a raw answer or free text cannot be represented.
 *
 * <p>The DOCX v1 key {@code life_mode:day_number:cycle_state:week_number:overlays:lang} is not used: it
 * lacks {@code phase_code}, {@code trimester} and versions. {@link #fingerprint()} covers the purpose,
 * all prompt inputs (overlay keys sorted) and the versions of the prompts, the output validator, the
 * overlay adapter, the calendar facts / phase code rules and the content rules, so changing any of them
 * is a cache miss. RU and EN are distinct keys.
 */
public record MyDayTemplateKey(
    String lifeMode,
    int dayNumber,
    String cycleState,
    String phaseCode,
    Integer weekNumber,
    String trimester,
    List<String> overlayKeys,
    String userLanguage,
    Versions versions
) {
    public static final String KEY_VERSION = "myday.template-key.v1";
    public static final String PURPOSE = "MY_DAY";
    public static final Set<String> LIFE_MODES = Set.of("teen_menarche", "adult_cycle", "planning", "pregnancy",
        "postpartum", "perimenopause", "menopause");
    public static final Set<String> CYCLE_STATES = Set.of("menstrual", "follicular", "fertile_window",
        "ovulation_peak", "luteal", "uncertain", "not_applicable");
    public static final Set<String> TRIMESTERS = Set.of("trimester_1", "trimester_2", "trimester_3");
    public static final Set<String> LANGUAGES = Set.of("ru", "en");
    private static final Pattern PHASE_CODE = Pattern.compile("[a-z][a-z0-9_]{0,39}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    public record Versions(String prompts, String validator, String overlayAdapter, String calendarFacts,
                           String contentRules) {
        public static Versions current() {
            return new Versions("myday.prompts.v1.1", "MY_DAY_CARDS_V1", MyDayOverlayAdapter.VERSION,
                MyDayCalendarFacts.VERSION + ":" + MyDayCalendarFacts.PHASE_CODE_VERSION, "myday.content-rules.v1");
        }

        public Versions {
            for (var value : List.of(prompts, validator, overlayAdapter, calendarFacts, contentRules)) {
                if (value == null || !VERSION.matcher(value).matches()) {
                    throw new IllegalArgumentException("Template key version is invalid");
                }
            }
        }
    }

    public MyDayTemplateKey {
        require(LIFE_MODES.contains(lifeMode), "life_mode");
        require(dayNumber >= 1, "day_number");
        require(CYCLE_STATES.contains(cycleState), "cycle_state");
        require(phaseCode != null && PHASE_CODE.matcher(phaseCode).matches(), "phase_code");
        require(weekNumber == null || (weekNumber >= 1 && weekNumber <= 52), "week_number");
        require(trimester == null || TRIMESTERS.contains(trimester), "trimester");
        require(LANGUAGES.contains(userLanguage), "user_language");
        require(versions != null, "versions");
        Objects.requireNonNull(overlayKeys, "overlay_keys");
        var allowed = MyDayOverlayAdapter.keys(lifeMode);
        var sorted = new TreeSet<String>();
        for (var key : overlayKeys) {
            require(key != null && allowed.contains(key), "overlay_keys");
            sorted.add(key);
        }
        overlayKeys = List.copyOf(sorted);
    }

    /** SHA-256 of the canonical form; the template-cache key. */
    public String fingerprint() {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(canonical().getBytes(StandardCharsets.UTF_8));
            var out = new StringBuilder(64);
            for (byte b : bytes) out.append("%02x".formatted(b & 0xff));
            return out.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    /** Unambiguous: every value is from a closed vocabulary without '|' or '=' or ','. */
    public String canonical() {
        return String.join("|",
            KEY_VERSION,
            "purpose=" + PURPOSE,
            "life_mode=" + lifeMode,
            "day_number=" + dayNumber,
            "cycle_state=" + cycleState,
            "phase_code=" + phaseCode,
            "week_number=" + (weekNumber == null ? "null" : weekNumber),
            "trimester=" + (trimester == null ? "null" : trimester),
            "overlay_keys=" + String.join(",", overlayKeys),
            "user_language=" + userLanguage,
            "prompts=" + versions.prompts(),
            "validator=" + versions.validator(),
            "overlay_adapter=" + versions.overlayAdapter(),
            "calendar_facts=" + versions.calendarFacts(),
            "content_rules=" + versions.contentRules());
    }

    /** The prompt parameters exactly as the v1.1 templates name them. */
    public Map<String, Object> promptParameters() {
        var out = new LinkedHashMap<String, Object>();
        out.put("life_mode", lifeMode);
        out.put("day_number", dayNumber);
        out.put("cycle_state", cycleState);
        out.put("phase_code", phaseCode);
        out.put("week_number", weekNumber);
        out.put("trimester", trimester);
        out.put("overlay_keys", overlayKeys);
        out.put("user_language", userLanguage);
        return Collections.unmodifiableMap(out);
    }

    private static void require(boolean valid, String field) {
        if (!valid) throw new IllegalArgumentException("Template key field is invalid: " + field);
    }
}
