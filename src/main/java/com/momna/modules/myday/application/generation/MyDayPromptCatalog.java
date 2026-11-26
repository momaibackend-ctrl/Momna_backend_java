package com.momna.modules.myday.application.generation;

import com.momna.modules.myday.application.template.MyDayTemplateKey;
import com.momna.platform.ai.AiContracts.PromptDefinition;
import com.momna.platform.ai.AiOutputSchemaKind;
import com.momna.platform.ai.AiPromptSections;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The My Day prompts v1.2 (docs/myday/Momna-MyDay-LLM-Spec-All7-Periods-v1.2.md), one system and one user
 * prompt per life mode, kept verbatim in {@code myday/prompts/v1.2}. Two template filters of the spec are
 * resolved here rather than in the gateway: {@code overlay_keys | join(', ') | default('none')} and
 * {@code tone | default('none')}. Only {@link MyDayTemplateKey} fields reach the variables, so the prompt
 * is anonymous by construction.
 */
public final class MyDayPromptCatalog {
    /** Registry version of {@link MyDayTemplateKey#PROMPTS_VERSION}: v1.0 = 1, v1.1 = 2, v1.2 = 3. */
    public static final int PROMPT_VERSION = 3;
    public static final int OUTPUT_SCHEMA_VERSION = 1;
    public static final String RESOURCE_DIR = "myday/prompts/v1.2/";
    public static final String OVERLAY_FILTER = "{{overlay_keys | join(', ') | default('none')}}";
    public static final String TONE_FILTER = "{{tone | default('none')}}";
    public static final Set<String> VARIABLES = Set.of("life_mode", "day_number", "cycle_state", "phase_code",
        "week_number", "trimester_or_null", "overlay_keys", "user_language", "tone");

    private MyDayPromptCatalog() {}

    public static String promptKey(String lifeMode) {
        return "myday.cards." + lifeMode;
    }

    public static List<PromptDefinition> load() {
        var out = new ArrayList<PromptDefinition>();
        for (var mode : new TreeSet<>(MyDayTemplateKey.LIFE_MODES)) {
            var system = resource(mode + ".system.txt");
            var user = resource(mode + ".user.txt");
            if (!user.contains(OVERLAY_FILTER) || !user.contains(TONE_FILTER)) {
                throw new IllegalStateException("My Day prompt v1.2 changed its filters: " + mode);
            }
            user = user.replace(OVERLAY_FILTER, "{{overlay_keys}}").replace(TONE_FILTER, "{{tone}}");
            out.add(new PromptDefinition(promptKey(mode), PROMPT_VERSION, AiPromptSections.join(system, user),
                VARIABLES, AiOutputSchemaKind.MY_DAY_CARDS_V1, OUTPUT_SCHEMA_VERSION));
        }
        return List.copyOf(out);
    }

    /** The prompt variables of one template key, rendered as the spec's template shows them. */
    public static Map<String, String> variables(MyDayTemplateKey key) {
        var out = new HashMap<String, String>();
        out.put("life_mode", key.lifeMode());
        out.put("day_number", Integer.toString(key.dayNumber()));
        out.put("cycle_state", key.cycleState());
        out.put("phase_code", key.phaseCode());
        out.put("week_number", key.weekNumber() == null ? "null" : key.weekNumber().toString());
        out.put("trimester_or_null", key.trimester() == null ? "null" : key.trimester());
        out.put("overlay_keys", key.overlayKeys().isEmpty() ? "none" : String.join(", ", key.overlayKeys()));
        out.put("user_language", key.userLanguage());
        out.put("tone", key.tone() == null ? "none" : key.tone());
        return Map.copyOf(out);
    }

    static String resource(String name) {
        try (var in = MyDayPromptCatalog.class.getClassLoader().getResourceAsStream(RESOURCE_DIR + name)) {
            if (in == null) throw new IllegalStateException("My Day prompt is missing: " + name);
            var text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
