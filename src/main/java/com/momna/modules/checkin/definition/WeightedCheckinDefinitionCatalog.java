package com.momna.modules.checkin.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.domain.CheckinDefinitionPeriod;
import com.momna.modules.checkin.domain.CheckinPhase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class WeightedCheckinDefinitionCatalog {
    private static final String RESOURCE = "/checkin/momna_checkin_weighted_model_files.zip.b64";
    private static final String ENTRY = "daily_checkin_weighted_model_v3.json";

    private final Map<Key, Definition> definitions;

    public WeightedCheckinDefinitionCatalog(ObjectMapper objectMapper) {
        this.definitions = load(objectMapper);
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
        var value = latest(period, phase);
        if (value.definitionVersion() != version) {
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
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory() && entry.getName().substring(entry.getName().lastIndexOf('/') + 1).equals(ENTRY)) {
                    return zip.readAllBytes();
                }
            }
        }
        throw new IllegalStateException("Canonical check-in JSON missing");
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
