package com.momna.modules.checkin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CheckinMyDayAiOutputValidator {
    public static final String KEY = "checkin.myday.output";
    public static final int VERSION = 1;
    public static final int OUTPUT_SCHEMA_VERSION = 1;

    private final ObjectMapper mapper;
    private final Set<String> internalItemCodes;
    private final Pattern causalPattern = Pattern.compile(
        "\\b(estrogen|progesterone|hormone|hormonal)\\b.{0,40}\\b(caused|causes|because of|due to|responsible for)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private final Pattern internalPattern = Pattern.compile(
        "\\b(raw[_ -]?answer|clarification[_ -]?value|safety[_ -]?payload)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public CheckinMyDayAiOutputValidator(
        ObjectMapper mapper,
        WeightedCheckinDefinitionCatalog definitions
    ) {
        this.mapper = mapper;
        this.internalItemCodes = definitions.all().stream()
            .flatMap(definition -> definition.items().stream())
            .map(WeightedCheckinDefinitionCatalog.Item::itemCode)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public ValidationResult validate(String text) {
        try {
            var content = parse(text);
            var visible = String.join(
                "\n",
                java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(content.headline(), content.summary()),
                    content.actions().stream()
                ).toList()
            );

            for (var code : internalItemCodes) {
                var pattern = Pattern.compile(
                    "(?<![A-Za-z0-9])" + Pattern.quote(code) + "(?![A-Za-z0-9])"
                );
                if (pattern.matcher(visible).find()) {
                    return new ValidationResult(false, "INTERNAL_CODE_LEAK", null);
                }
            }
            if (causalPattern.matcher(visible).find()) {
                return new ValidationResult(false, "UNSUPPORTED_HORMONAL_CAUSALITY", null);
            }
            if (internalPattern.matcher(visible).find()) {
                return new ValidationResult(false, "INTERNAL_PAYLOAD_LEAK", null);
            }
            return new ValidationResult(true, "OK", content);
        } catch (RuntimeException failure) {
            return new ValidationResult(false, "MALFORMED_JSON", null);
        }
    }

    public CheckinMyDayAiContent parse(String text) {
        try {
            JsonNode root = mapper.readTree(text);
            if (!root.isObject()) throw new IllegalArgumentException("schema");

            var names = new HashSet<String>();
            root.fieldNames().forEachRemaining(names::add);
            if (!names.equals(Set.of("schemaVersion", "headline", "summary", "actions"))) {
                throw new IllegalArgumentException("schema");
            }
            if (root.path("schemaVersion").asInt(-1) != OUTPUT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("schema");
            }
            if (!root.path("headline").isTextual() || !root.path("summary").isTextual()
                || !root.path("actions").isArray()) {
                throw new IllegalArgumentException("schema");
            }

            var actions = new ArrayList<String>();
            for (var action : root.path("actions")) {
                if (!action.isTextual()) throw new IllegalArgumentException("schema");
                actions.add(action.asText());
            }
            return new CheckinMyDayAiContent(
                root.path("headline").asText(),
                root.path("summary").asText(),
                List.copyOf(actions)
            );
        } catch (Exception failure) {
            throw new IllegalArgumentException("Invalid Check-in My Day AI output", failure);
        }
    }

    public record ValidationResult(
        boolean valid,
        String reason,
        CheckinMyDayAiContent content
    ) {}
}
