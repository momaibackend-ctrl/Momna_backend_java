package com.momna.platform.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The ER0 "I feel very unwell now" screen of catalog v2.2 §8: its texts and the signs shown per period. */
@Component
public class MedicalSafetyEr0Catalog {
    private static final String RESOURCE = "/safety/medical-safety-er0-v2.3.json";

    public record Text(String ru, String enUs) {}

    public record Option(String code, String period, String routeId, Text label) {}

    /** The ER0 screen for one period, with the period's own signs after the common ones. */
    public record Screen(String catalogVersion, Text entry, Text call, Text title, Text supporting, Text none,
        List<Option> options) {}

    private final Map<String, Text> texts = new LinkedHashMap<>();
    private final List<Map<String, String>> options = new ArrayList<>();

    public MedicalSafetyEr0Catalog(ObjectMapper mapper) {
        try (var stream = MedicalSafetyEr0Catalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("ER0 catalog missing");
            var root = mapper.readTree(stream);
            if (!MedicalSafetyCatalog.VERSION.equals(root.path("catalogVersion").asText())) {
                throw new IllegalStateException("Unexpected ER0 catalog version");
            }
            for (var key : List.of("entry", "call", "title", "supporting", "none")) {
                texts.put(key, new Text(root.path(key).path("ru").asText(), root.path(key).path("enUs").asText()));
            }
            for (var node : root.path("options")) {
                var option = new LinkedHashMap<String, String>();
                node.fieldNames().forEachRemaining(name -> option.put(name, node.path(name).asText()));
                options.add(option);
            }
            if (options.size() != 16) throw new IllegalStateException("Incomplete ER0 catalog");
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load the ER0 catalog", failure);
        }
    }

    public Screen screen(String period) {
        var shown = new ArrayList<Option>();
        for (var o : options) {
            if (!"ALL".equals(o.get("period")) && !o.get("period").equals(period)) continue;
            boolean postpartum = "POSTPARTUM".equals(period) && o.containsKey("postpartumRu");
            shown.add(new Option(o.get("code"), o.get("period"), o.get("routeId"), postpartum
                ? new Text(o.get("postpartumRu"), o.get("postpartumEnUs"))
                : new Text(o.get("ru"), o.get("enUs"))));
        }
        return new Screen(MedicalSafetyCatalog.VERSION, texts.get("entry"), texts.get("call"), texts.get("title"),
            texts.get("supporting"), texts.get("none"), List.copyOf(shown));
    }

    /** The option a tap selected, if the period shows it. */
    public Option option(String period, String code) {
        return screen(period).options().stream().filter(o -> o.code().equals(code)).findFirst()
            .orElseThrow(() -> new MedicalSafetyException("SAFETY_SIGNAL_NOT_APPLICABLE",
                "ER0 sign is not shown in this period"));
    }
}
