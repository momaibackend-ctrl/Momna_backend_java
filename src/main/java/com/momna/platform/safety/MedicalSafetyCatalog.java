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
    private final Set<String> conditionalFacts;
    private final Set<String> hemorrhageFacts;
    private final String hemorrhageRoute;
    private final Set<String> infectionRoutes;
    private final Set<String> infectionCompanions;
    private final Set<String> bpRoutes;
    private final Set<String> bpCombinationSigns;
    private final int severeSystolic, severeDiastolic, elevatedSystolic, elevatedDiastolic;
    private final Set<String> systemicRoutes, systemicFacts;
    public enum PressureBand { UNKNOWN, BELOW_THRESHOLD, ELEVATED, SEVERE }

    public MedicalSafetyCatalog(ObjectMapper mapper) {
        try (var stream = MedicalSafetyCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Safety catalog missing");
            var root = mapper.readTree(stream);
            if (!VERSION.equals(root.path("catalogVersion").asText())) {
                throw new IllegalStateException("Unexpected safety catalog version");
            }
            var rules = root.path("policies");
            if (rules.path("schemaVersion").asInt() != 1
                || !rules.path("bloodPressure").path("provisional").asBoolean()) {
                throw new IllegalStateException("Missing structured provisional safety policies");
            }
            this.conditionalFacts = Set.copyOf(strings(rules.path("conditionUndefinedFacts")));
            this.hemorrhageFacts = Set.copyOf(strings(rules.path("postpartumBleeding").path("emergencyIfYes")));
            this.hemorrhageRoute = rules.path("postpartumBleeding").path("routeId").asText();
            this.infectionRoutes = Set.copyOf(strings(rules.path("postpartumInfection").path("routeIds")));
            this.infectionCompanions = Set.copyOf(strings(rules.path("postpartumInfection").path("otherFacts")));
            var bp = rules.path("bloodPressure");
            this.bpRoutes = Set.copyOf(strings(bp.path("routeIds")));
            this.bpCombinationSigns = Set.copyOf(strings(bp.path("comboSigns")));
            this.severeSystolic = bp.path("severeSystolic").asInt();
            this.severeDiastolic = bp.path("severeDiastolic").asInt();
            this.elevatedSystolic = bp.path("elevatedSystolic").asInt();
            this.elevatedDiastolic = bp.path("elevatedDiastolic").asInt();
            var qm = rules.path("checklist");
            this.systemicRoutes = Set.copyOf(strings(qm.path("routeIds")));
            this.systemicFacts = Set.copyOf(strings(qm.path("factCodes")));
            if (elevatedSystolic >= severeSystolic || elevatedDiastolic >= severeDiastolic
                || systemicFacts.size() != 5) throw new IllegalStateException("Invalid structured safety policy");
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
                    entry.path("rule").asText(),
                    entry.path("postpartumRu").asText(null),
                    entry.path("postpartumEnUs").asText(null),
                    entry.path("origin").asText("DIRECT"),
                    entry.path("questionEligible").asBoolean(true)
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

    public Set<String> conditionUndefinedFacts() { return conditionalFacts; }
    public Set<String> postpartumBleedingEmergencyFacts() { return hemorrhageFacts; }
    public String postpartumBleedingRoute() { return hemorrhageRoute; }
    public Set<String> postpartumInfectionRoutes() { return infectionRoutes; }
    public Set<String> postpartumInfectionCompanions() { return infectionCompanions; }
    public Set<String> bpCombinationFacts() { return bpCombinationSigns; }
    public Set<String> systemicChecklistRoutes() { return systemicRoutes; }
    public Set<String> systemicChecklistFacts() { return systemicFacts; }
    public java.util.List<String> pendingSystemicFacts(Route route, java.util.Map<String, String> known) {
        return route.questions().stream().filter(systemicFacts::contains)
            .filter(code -> !known.containsKey(code)).toList();
    }
    /** Only the versioned BP1 rule applies, and only for relevant routes. */
    public PressureBand classifyPressure(String routeId, String reading) {
        if (!bpRoutes.contains(routeId) || reading == null || reading.equals("UNKNOWN")) {
            return PressureBand.UNKNOWN;
        }
        if (!reading.matches("[0-9]{2,3}/[0-9]{2,3}")) {
            throw new IllegalArgumentException("Invalid saved blood-pressure reading");
        }
        var numbers = reading.split("/");
        int systolic = Integer.parseInt(numbers[0]), diastolic = Integer.parseInt(numbers[1]);
        if (systolic <= 0 || diastolic <= 0 || systolic > 500 || diastolic > 500) {
            throw new IllegalArgumentException("Invalid saved blood-pressure values");
        }
        if (systolic >= severeSystolic || diastolic >= severeDiastolic) return PressureBand.SEVERE;
        if (systolic >= elevatedSystolic || diastolic >= elevatedDiastolic) return PressureBand.ELEVATED;
        return PressureBand.BELOW_THRESHOLD;
    }
    public JsonNode localizedContent() { return localizedContent.deepCopy(); }
    public Collection<Route> routes() { return routes.values(); }
    public Collection<Fact> facts() { return facts.values(); }
    /** API-only projection: never serialize technical rule text to end users. */
    public List<PublicFact> publicFacts(String period) {
        return facts.values().stream().map(f -> new PublicFact(
            f.code(),
            "POSTPARTUM".equals(period) && f.postpartumRu() != null ? f.postpartumRu() : f.ru(),
            "POSTPARTUM".equals(period) && f.postpartumEnUs() != null
                ? f.postpartumEnUs() : f.enUs(),
            f.role(), f.origin(), f.questionEligible()
        )).toList();
    }

    public List<PublicRoute> publicRoutes() {
        return routes.values().stream().map(r -> new PublicRoute(
            r.routeId(), r.routeCode(), r.period(), r.itemCodes(),
            r.questions(), r.cooldownHours()
        )).toList();
    }

    public List<QuestionGroup> questionGroups() {
        return List.of(new QuestionGroup("QM_SYSTEMIC",
            List.of("F_FEVER_38", "F_HEART_RACING", "F_SEVERE_VOMITING",
                "F_PRESYNCOPE", "F_EXTREME_WEAKNESS"),
            List.of("R9", "R16")));
    }
    public record PublicFact(String factCode, String ru, String enUs,
        String role, String origin, boolean questionEligible) {}
    public record PublicRoute(String routeId, String routeCode, String period,
        List<String> itemCodes, List<String> questions, int cooldownHours) {}
    public record QuestionGroup(String groupCode, List<String> factCodes,
        List<String> routeIds) {}
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
    public record Fact(String code, String ru, String enUs, String role, String rule,
        String postpartumRu, String postpartumEnUs, String origin, boolean questionEligible) {}
}
