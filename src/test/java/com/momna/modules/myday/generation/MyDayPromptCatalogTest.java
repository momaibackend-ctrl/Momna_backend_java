package com.momna.modules.myday.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.momna.modules.myday.application.generation.MyDayPromptCatalog;
import com.momna.modules.myday.application.template.MyDayTemplateKey;
import com.momna.platform.ai.AiOutputSchemaKind;
import com.momna.platform.ai.AiPromptSections;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The prompts v1.2 resources are the spec's text, word for word, and render with the template key only. */
class MyDayPromptCatalogTest {
    private static final Path SPEC = Path.of("docs/myday/Momna-MyDay-LLM-Spec-All7-Periods-v1.2.md");
    private static final Path RESOURCES = Path.of("src/main/resources", MyDayPromptCatalog.RESOURCE_DIR);

    @Test
    void everyPromptIsTheSpecTextVerbatim() throws Exception {
        var spec = Files.readString(SPEC).replace("\r\n", "\n");
        var sections = spec.split("\n## \\d+\\. ");
        for (var mode : new TreeSet<>(MyDayTemplateKey.LIFE_MODES)) {
            String section = null;
            for (var s : sections) if (s.startsWith(mode + " ")) section = s;
            assertNotNull(section, "spec section " + mode);
            assertEquals(block(section, "System prompt"), read(mode + ".system.txt"), mode + " system prompt");
            assertEquals(block(section, "User prompt"), read(mode + ".user.txt"), mode + " user prompt");
        }
    }

    @Test
    void everyLifeModeHasOneMyDayPromptWithTheSpecVariables() {
        var prompts = MyDayPromptCatalog.load();
        assertEquals(7, prompts.size());
        for (var prompt : prompts) {
            assertEquals(MyDayPromptCatalog.PROMPT_VERSION, prompt.version());
            assertEquals(AiOutputSchemaKind.MY_DAY_CARDS_V1, prompt.outputSchemaKind());
            var used = new TreeSet<String>();
            var matcher = Pattern.compile("\\{\\{([a-z_]+)}}").matcher(prompt.template());
            while (matcher.find()) used.add(matcher.group(1));
            assertEquals(new TreeSet<>(MyDayPromptCatalog.VARIABLES), used, prompt.key());
            assertFalse(prompt.template().contains("|"+" join"), prompt.key());
            var sections = AiPromptSections.split(prompt.template());
            assertTrue(sections.system().startsWith("You are a content writer for Momna"), prompt.key());
            assertTrue(sections.user().startsWith("Generate 9"), prompt.key());
        }
    }

    @Test
    void theVariablesComeFromTheTemplateKeyAsTheSpecShowsThem() {
        var key = new MyDayTemplateKey("pregnancy", 3, "not_applicable", "preg_trimester2_w20", 20, "trimester_2",
            List.of("preg_health_flag", "preg_high_risk"), "ru", "direct", MyDayTemplateKey.Versions.current());
        var vars = MyDayPromptCatalog.variables(key);
        assertEquals(MyDayPromptCatalog.VARIABLES, vars.keySet());
        assertEquals("preg_health_flag, preg_high_risk", vars.get("overlay_keys"));
        assertEquals("trimester_2", vars.get("trimester_or_null"));
        assertEquals("20", vars.get("week_number"));
        assertEquals("direct", vars.get("tone"));

        var plain = new MyDayTemplateKey("adult_cycle", 1, "luteal", "adult_luteal", null, null, List.of(), "en", null,
            MyDayTemplateKey.Versions.current());
        var none = MyDayPromptCatalog.variables(plain);
        assertEquals("none", none.get("overlay_keys"));
        assertEquals("none", none.get("tone"));
        assertEquals("null", none.get("week_number"));
        assertEquals("null", none.get("trimester_or_null"));
    }

    private static String block(String section, String heading) {
        var after = section.split("### " + heading + "\n", 2)[1];
        var start = after.indexOf("```text\n") + "```text\n".length();
        return after.substring(start, after.indexOf("\n```", start));
    }

    private static String read(String name) throws Exception {
        var text = Files.readString(RESOURCES.resolve(name)).replace("\r\n", "\n");
        return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
    }
}
