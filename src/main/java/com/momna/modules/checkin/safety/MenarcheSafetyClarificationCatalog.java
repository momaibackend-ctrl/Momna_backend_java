package com.momna.modules.checkin.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuration adapter for the Kotlin CheckinSafetyClarificationContract.
 * Option value -> signal keys are server-owned; clients cannot choose signals.
 */
public final class MenarcheSafetyClarificationCatalog {
    public record Option(String value, String labelKey, Set<String> signalKeys) {}
    public record Definition(String routeCode, String presentationKey, List<Option> options) {}

    private final Map<String, Definition> definitions;

    public MenarcheSafetyClarificationCatalog() {
        this.definitions = load();
    }

    public Definition definition(String route) {
        var result = definitions.get(route);
        if (result == null) throw new IllegalStateException("Unknown safety clarification route");
        return result;
    }

    public Option option(String route, String value) {
        return definition(route).options().stream()
            .filter(o -> o.value().equals(value))
            .findFirst().orElse(null);
    }

    private Map<String, Definition> load() {
        try (var input = getClass().getResourceAsStream(
            "/checkin/menarche-safety-clarifications.json")) {
            if (input == null) throw new IllegalStateException("Menarche safety catalog missing");
            var root = new ObjectMapper().readTree(input);
            if (root.path("schemaVersion").asInt() != 1 || !root.path("routes").isArray()) {
                throw new IllegalStateException("Invalid Menarche safety catalog version");
            }
            var definitions = new LinkedHashMap<String, Definition>();
            for (var entry : root.path("routes")) {
                String route = entry.path("routeCode").asText();
                String presentationKey = entry.path("presentationKey").asText();
                var options = new ArrayList<Option>();
                var values = new HashSet<String>();
                for (var item : entry.path("options")) {
                    String value = item.path("value").asText();
                    if (!values.add(value)) throw new IllegalStateException("Duplicate safety answer");
                    var signals = new LinkedHashSet<String>();
                    for (var signal : item.path("signalKeys")) {
                        String code = signal.asText();
                        if (!Set.of("MEDICAL_EMERGENCY", "URGENT_CLINICAL_RISK").contains(code)) {
                            throw new IllegalStateException("Unregistered safety signal: " + code);
                        }
                        signals.add(code);
                    }
                    options.add(new Option(value, item.path("labelKey").asText(),
                        Set.copyOf(signals)));
                }
                if (route.isBlank() || presentationKey.isBlank() || options.isEmpty()) {
                    throw new IllegalStateException("Incomplete Menarche safety route");
                }
                if (definitions.putIfAbsent(route,
                    new Definition(route, presentationKey, List.copyOf(options))) != null) {
                    throw new IllegalStateException("Duplicate Menarche safety route");
                }
            }
            if (!definitions.keySet().containsAll(Set.of(
                "MENARCHE_HEADACHE", "MENARCHE_DIZZINESS",
                "MENARCHE_BLEEDING", "SAFETY_REVIEW_REQUIRED"))) {
                throw new IllegalStateException("Missing required Menarche safety routes");
            }
            return Map.copyOf(definitions);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load Menarche safety rules", failure);
        }
    }
}
