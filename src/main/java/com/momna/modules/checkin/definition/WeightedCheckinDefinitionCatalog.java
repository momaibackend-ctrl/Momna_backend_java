package com.momna.modules.checkin.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.domain.CheckinDefinitionPeriod;
import com.momna.modules.checkin.domain.CheckinPhase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Component;

@Component
public class WeightedCheckinDefinitionCatalog {
    private static final String RESOURCE = "/checkin/momna_checkin_weighted_model_files.zip.b64";
    private static final String ENTRY = "daily_checkin_weighted_model_v3.json";
    private static final String ADDENDUM = "/checkin/daily-checkin-weighted-v3.1-addendum.json";

    private final Map<Key, Definition> definitions;
    private final Map<Key, Definition> historicV3;

    public WeightedCheckinDefinitionCatalog(ObjectMapper objectMapper) {
        this.historicV3 = load(objectMapper);
        this.definitions = overlayV31(objectMapper, historicV3);
    }

    public List<Definition> all() {
        return definitions.values().stream()
            .sorted(Comparator
                .comparing((Definition x) -> x.period().ordinal())
                .thenComparing(x -> x.phase().ordinal()))
            .toList();
    }

    public Definition latest(CheckinDefinitionPeriod period, CheckinPhase phase) {
        var value = definitions.get(new Key(period, phase));
        if (value == null) throw new IllegalStateException("Check-in definition unavailable");
        return value;
    }

    public Definition get(CheckinDefinitionPeriod period, CheckinPhase phase, int version) {
        var value = version == 3 ? historicV3.get(new Key(period, phase)) : latest(period, phase);
        if (value == null || value.definitionVersion() != version) {
            throw new IllegalStateException("Check-in definition version unavailable");
        }
        return value;
    }

    private Map<Key, Definition> load(ObjectMapper mapper) {
        try {
            var stream = WeightedCheckinDefinitionCatalog.class.getResourceAsStream(RESOURCE);
            if (stream == null) throw new IllegalStateException("Check-in source bundle missing");

            var encoded = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\s+", "");
            var zip = Base64.getDecoder().decode(encoded);
            var jsonBytes = extractJson(zip);
            var sha = hex(MessageDigest.getInstance("SHA-256").digest(jsonBytes));
            var root = mapper.readTree(jsonBytes);

            var multipliers = root.path("profile_multipliers");
            var out = new LinkedHashMap<Key, Definition>();

            for (var periodNode : root.path("periods")) {
                var period = mapPeriod(periodNode.path("code").asText());
                loadPhase(out, period, CheckinPhase.MORNING, "morning_items", periodNode, multipliers, sha);
                loadPhase(out, period, CheckinPhase.EVENING, "evening_items", periodNode, multipliers, sha);
            }

            var itemCount = out.values().stream().mapToInt(x -> x.items().size()).sum();
            if (out.size() != 14 || itemCount != 99) {
                throw new IllegalStateException("Unexpected weighted check-in source size");
            }
            return Map.copyOf(out);
        } catch (Exception failure) {
            if (failure instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Unable to load weighted check-in definitions", failure);
        }
    }

    private Map<Key, Definition> overlayV31(ObjectMapper mapper, Map<Key, Definition> v3) {
        try (var stream = WeightedCheckinDefinitionCatalog.class.getResourceAsStream(ADDENDUM)) {
            if (stream == null) throw new IllegalStateException("Weighted v3.1 addendum unavailable");
            var bytes = stream.readAllBytes();
            var root = mapper.readTree(bytes);
            if (!"3.1".equals(root.path("schemaVersion").asText())) {
                throw new IllegalStateException("Unexpected weighted v3.1 version");
            }
            var overlaySha = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
            var result = new LinkedHashMap<Key, Definition>();
            for (var entry : v3.entrySet()) {
                var baseline = entry.getValue();
                var items = new ArrayList<>(baseline.items());
                for (var node : root.path("items")) {
                    if (!node.path("period").asText().equals(baseline.period().name())) continue;
                    var applies = false;
                    for (var phase : node.path("phases")) {
                        if (phase.asText().equals(baseline.phase().name())) applies = true;
                    }
                    if (!applies) continue;
                    if (items.stream().anyMatch(item -> item.itemCode().equals(node.path("code").asText()))) {
                        throw new IllegalStateException("Check-in v3.1 code collision");
                    }
                    var states = new ArrayList<State>();
                    for (var state : node.path("states")) {
                        states.add(new State(states.size(), state.path("ru").asText(), state.path("en").asText()));
                    }
                    if (states.size() != 3) throw new IllegalStateException("Invalid v3.1 state count");
                    // No product-approved numeric weights: do not alter the original scoring.
                    // Medical direct entry depends only on the saved state and catalog binding.
                    items.add(new Item(
                        node.path("code").asText(), items.size() + 1,
                        baseline.phase().name().toLowerCase(Locale.ROOT),
                        node.path("label_ru").asText(), node.path("label_en").asText(),
                        List.copyOf(states), false, 0.0, 0.0, List.of()
                    ));
                }
                result.put(entry.getKey(), new Definition(
                    baseline.definitionKey(), baseline.period(), baseline.phase(), 4,
                    "weighted-v3.1", "3.1", baseline.sourcePeriodCode(),
                    baseline.sourceModel(), baseline.sourceContentSha256() + ":" + overlaySha,
                    List.copyOf(items)
                ));
            }
            if (result.values().stream().mapToInt(x -> x.items().size()).sum() != 102) {
                throw new IllegalStateException("Weighted v3.1 expected 102 displayed items");
            }
            return Map.copyOf(result);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to load weighted v3.1", failure);
        }
    }

    private void loadPhase(
        Map<Key, Definition> out,
        CheckinDefinitionPeriod period,
        CheckinPhase phase,
        String itemsKey,
        JsonNode periodNode,
        JsonNode multipliers,
        String sha
    ) {
        var items = new ArrayList<Item>();
        var source = periodNode.path(itemsKey);
        for (int i = 0; i < source.size(); i++) {
            var node = source.get(i);
            var code = node.path("code").asText();
            var rules = new ArrayList<Multiplier>();

            for (var multiplier : multipliers) {
                if (!code.equals(multiplier.path("target_item").asText())) continue;
                rules.add(new Multiplier(
                    multiplier.path("id").asText(),
                    multiplier.path("profile_field").asText(),
                    multiplier.path("value").asText(),
                    multiplier.path("target_weight").asText(),
                    multiplier.path("factor").asDouble()
                ));
            }

            var states = new ArrayList<State>();
            var stateNodes = node.path("states");
            for (int state = 0; state < stateNodes.size(); state++) {
                var stateNode = stateNodes.get(state);
                states.add(new State(
                    state,
                    stateNode.path("ru").asText(),
                    stateNode.path("en").asText()
                ));
            }

            var scored = node.path("scored").asBoolean();
            items.add(new Item(
                code,
                i + 1,
                phase.name().toLowerCase(Locale.ROOT),
                node.path("label_ru").asText(),
                node.path("label_en").asText(),
                List.copyOf(states),
                scored,
                node.path("adj_w").isNumber() ? node.path("adj_w").asDouble() : 0.0,
                node.path("safety_w").isNumber() ? node.path("safety_w").asDouble() : 0.0,
                List.copyOf(rules)
            ));
        }

        var definition = new Definition(
            "daily-checkin." + period.name().toLowerCase(Locale.ROOT) + "." + phase.name().toLowerCase(Locale.ROOT),
            period,
            phase,
            3,
            "weighted-v3",
            "3.0",
            periodNode.path("code").asText(),
            "weighted_scoring",
            sha,
            List.copyOf(items)
        );
        out.put(new Key(period, phase), definition);
    }

    private byte[] extractJson(byte[] archive) throws IOException {
        var temporary = Files.createTempFile("momna-checkin-model-", ".zip");
        try {
            Files.write(temporary, archive);
            try (var zip = new ZipFile(temporary.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.isDirectory()
                        && entry.getName().substring(entry.getName().lastIndexOf('/') + 1).equals(ENTRY)) {
                        try (var input = zip.getInputStream(entry)) {
                            return input.readAllBytes();
                        }
                    }
                }
            }
            throw new IllegalStateException("Canonical check-in JSON missing");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private CheckinDefinitionPeriod mapPeriod(String code) {
        return switch (code) {
            case "MENARCHE" -> CheckinDefinitionPeriod.MENARCHE;
            case "ADULT_CYCLE" -> CheckinDefinitionPeriod.CYCLE;
            case "PLANNING_PREGNANCY" -> CheckinDefinitionPeriod.PLANNING;
            case "PREGNANCY" -> CheckinDefinitionPeriod.PREGNANCY;
            case "POSTPARTUM_YEAR_ONE" -> CheckinDefinitionPeriod.POSTPARTUM;
            case "PERIMENOPAUSE" -> CheckinDefinitionPeriod.PERIMENOPAUSE;
            case "MENOPAUSE" -> CheckinDefinitionPeriod.MENOPAUSE;
            default -> throw new IllegalStateException("Unknown check-in period: " + code);
        };
    }

    private String hex(byte[] bytes) {
        var out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append("%02x".formatted(value & 0xff));
        return out.toString();
    }

    public record Key(CheckinDefinitionPeriod period, CheckinPhase phase) {}

    public record Definition(
        String definitionKey,
        CheckinDefinitionPeriod period,
        CheckinPhase phase,
        int definitionVersion,
        String poolVersion,
        String sourceSchemaVersion,
        String sourcePeriodCode,
        String sourceModel,
        String sourceContentSha256,
        List<Item> items
    ) {}

    public record Item(
        String itemCode,
        int displayOrder,
        String group,
        String labelRu,
        String labelEnUs,
        List<State> states,
        boolean scored,
        double adjustmentWeight,
        double safetyWeight,
        List<Multiplier> profileMultipliers
    ) {}

    public record State(int value, String ru, String enUs) {}

    public record Multiplier(
        String ruleId,
        String profileField,
        String value,
        String targetWeight,
        double multiplier
    ) {}
}
