package com.momna.platform.safety;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

/** Versioned product catalog; clinical-release approval is deliberately separate. */
@Component
public final class MedicalSafetyCatalog {
    public static final String VERSION = "medical-safety-v2.2";
    public static final String RULE_VERSION = "medical-safety-rules-v2.2";
    private static final String RESOURCE = "/safety/medical-safety-catalog-v2.2.json";

    private final Map<String, Route> routes;
    private final Map<String, Fact> facts;
    private final JsonNode localizedContent;

    public MedicalSafetyCatalog(ObjectMapper mapper) {
        try (var stream = MedicalSafetyCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Safety catalog missing");
            var root = mapper.readTree(stream);
            if (!VERSION.equals(root.path("catalogVersion").asText())) {
                throw new IllegalStateException("Unexpected safety catalog version");
            }
            var routeMap = new LinkedHashMap<String, Route>();
            for (JsonNode entry : root.path("routes")) {
                var route = new Route(
                    entry.path("routeId").asText(), entry.path("routeCode").asText(),
                    entry.path("period").asText(), strings(entry.path("itemCodes")),
                    strings(entry.path("questions")), entry.path("cooldownHours").asInt(),
                    entry.path("crossChecks").asText()
                );
                if (routeMap.putIfAbsent(route.routeId(), route) != null) {
                    throw new IllegalStateException("Duplicate route " + route.routeId());
                }
            }
            var factMap = new LinkedHashMap<String, Fact>();
            for (JsonNode entry : root.path("facts")) {
                var fact = new Fact(
                    entry.path("factCode").asText(), entry.path("ru").asText(),
                    entry.path("enUs").asText(), entry.path("role").asText(),
                    entry.path("rule").asText()
                );
                if (factMap.putIfAbsent(fact.code(), fact) != null) {
                    throw new IllegalStateException("Duplicate fact " + fact.code());
                }
            }
            if (routeMap.size() != 28 || factMap.size() != 53) {
                throw new IllegalStateException("Incomplete medical safety catalog");
            }
            for (var route : routeMap.values()) {
                for (var code : route.questions()) {
                    if (!factMap.containsKey(code)) throw new IllegalStateException("Unknown fact " + code);
                }
            }
            this.routes = Collections.unmodifiableMap(routeMap);
            this.facts = Collections.unmodifiableMap(factMap);
            try (var content = MedicalSafetyCatalog.class.getResourceAsStream(
                "/safety/medical-safety-content-v2.2.json")) {
                if (content == null) throw new IllegalStateException("Safety screen content missing");
                this.localizedContent = mapper.readTree(content);
                if (this.localizedContent.path("screens").size() != 22
                    || this.localizedContent.path("combinations").size() != 6) {
                    throw new IllegalStateException("Incomplete Safety localized content");
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load safety catalog", failure);
        }
    }

    private static List<String> strings(JsonNode node) {
        var result = new ArrayList<String>();
        for (var part : node) result.add(part.asText());
        return List.copyOf(result);
    }

    public JsonNode localizedContent() { return localizedContent.deepCopy(); }
    public Collection<Route> routes() { return routes.values(); }
    public Collection<Fact> facts() { return facts.values(); }
    public Route route(String routeId) {
        var value = routes.get(routeId);
        if (value == null) throw new IllegalArgumentException("Unknown safety route");
        return value;
    }
    public Fact fact(String code) {
        var value = facts.get(code);
        if (value == null) throw new IllegalArgumentException("Unknown safety fact");
        return value;
    }
    public List<Route> forItem(String period, String itemCode) {
        if (itemCode == null) return List.of();
        return routes.values().stream()
            .filter(route -> route.period().equals(period) && route.itemCodes().contains(itemCode))
            .toList();
    }

    public List<Route> applicable(String period, String itemCode, Integer state) {
        if (itemCode == null || state == null) return List.of();
        // PP2=1 means "emotionally hard"; PP2=2 means "not sure yet".
        var active = "PP2".equals(itemCode) ? state == 1 : state == 2;
        if (!active) return List.of();
        return routes.values().stream()
            .filter(route -> (route.period().equals(period) || route.period().equals("GLOBAL"))
                && route.itemCodes().contains(itemCode))
            .toList();
    }
    public record Route(String routeId, String routeCode, String period,
                        List<String> itemCodes, List<String> questions,
                        int cooldownHours, String crossChecks) {}
    public record Fact(String code, String ru, String enUs, String role, String rule) {}
}
