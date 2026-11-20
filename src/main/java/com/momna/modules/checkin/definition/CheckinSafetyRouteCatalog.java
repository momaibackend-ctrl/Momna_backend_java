package com.momna.modules.checkin.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.definition.CheckinSafetyCatalogException.Code;
import com.momna.modules.checkin.domain.CheckinPhase;
import com.momna.platform.safety.CanonicalSafetyCatalog;
import com.momna.platform.safety.SafetyDisposition;
import com.momna.platform.safety.SafetySeverity;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Check-in safety routes and item bindings from the governed weighted model v3
 * ({@code safety_route_catalog}, {@code item_safety_routes}). Every scored item with
 * {@code safety_w > 0} binds to exactly one route, in all seven lifecycle periods.
 * This class selects a route; the safety platform owns the decision.
 */
@Component
public final class CheckinSafetyRouteCatalog {
    public static final String CATALOG_VERSION = "checkin.safety.routes.v3";
    public static final String RULE_VERSION = "checkin.safety.rules.v3";
    public static final String SOURCE_SCHEMA_VERSION = "3.0";
    public static final String OPEN_SAFETY_CLARIFICATION = "OPEN_SAFETY_CLARIFICATION";
    public static final int EXPECTED_ROUTE_COUNT = 20;
    public static final int EXPECTED_BINDING_COUNT = 22;

    public record RouteDefinition(
        String code,
        String governanceDescription,
        String contentRef,
        SafetyDisposition disposition,
        SafetySeverity severity,
        Set<String> requiredActions,
        Set<String> allowedActions
    ) {}

    public record Binding(
        String itemCode,
        String routeCode,
        CheckinPhase phase,
        int displayOrder,
        BigDecimal baseSafetyWeight
    ) {}

    public record ItemContribution(String itemCode, BigDecimal contribution) {}

    public record Selection(
        String itemCode,
        RouteDefinition route,
        BigDecimal contribution,
        int displayOrder,
        String catalogVersion
    ) {}

    public record Metadata(
        String catalogVersion,
        String ruleVersion,
        String sourceSchemaVersion,
        String sourceContentSha256,
        String fallbackRouteCode,
        int routeCount,
        int bindingCount
    ) {}

    private final List<RouteDefinition> routes;
    private final List<Binding> bindings;
    private final Metadata metadata;
    private final Map<String, RouteDefinition> routesByCode;
    private final Map<String, Binding> bindingsByItem;

    public CheckinSafetyRouteCatalog() {
        this(canonicalSource());
    }

    CheckinSafetyRouteCatalog(byte[] sourceJson) {
        var parsed = parse(sourceJson);
        this.routes = parsed.routes;
        this.bindings = parsed.bindings;
        this.metadata = parsed.metadata;
        this.routesByCode = index(routes, RouteDefinition::code);
        this.bindingsByItem = index(bindings, Binding::itemCode);
    }

    public static CheckinSafetyRouteCatalog fromJson(byte[] sourceJson) {
        return new CheckinSafetyRouteCatalog(sourceJson);
    }

    public List<RouteDefinition> routes() { return routes; }
    public List<Binding> bindings() { return bindings; }
    public Metadata metadata() { return metadata; }

    public RouteDefinition route(String code) { return route(code, CATALOG_VERSION); }

    public RouteDefinition route(String code, String version) {
        requireVersion(version);
        var route = routesByCode.get(code);
        if (route == null) throw new CheckinSafetyCatalogException(Code.UNKNOWN_ROUTE, "Unknown check-in safety route " + code);
        return route;
    }

    public Binding bindingForItem(String itemCode) { return bindingForItem(itemCode, CATALOG_VERSION); }

    public Binding bindingForItem(String itemCode, String version) {
        requireVersion(version);
        var binding = bindingsByItem.get(itemCode);
        if (binding == null) {
            throw new CheckinSafetyCatalogException(Code.UNKNOWN_ITEM, "Check-in item " + itemCode + " has no safety route binding");
        }
        return binding;
    }

    public Selection selectRoute(List<ItemContribution> contributions) {
        return selectRoute(contributions, CATALOG_VERSION);
    }

    /** Highest contribution wins; ties go to the lower display order, then the item code. */
    public Selection selectRoute(List<ItemContribution> contributions, String version) {
        requireVersion(version);
        if (contributions.isEmpty()
            || contributions.stream().map(ItemContribution::itemCode).distinct().count() != contributions.size()) {
            throw new CheckinSafetyCatalogException(Code.INVALID_CONTRIBUTION,
                "Safety route selection requires unique item contributions");
        }
        record Candidate(ItemContribution contribution, Binding binding) {}
        var winner = contributions.stream()
            .filter(c -> c.contribution().signum() > 0)
            .map(c -> new Candidate(c, bindingForItem(c.itemCode(), version)))
            .min(Comparator
                .comparing((Candidate c) -> c.contribution().contribution(), Comparator.reverseOrder())
                .thenComparingInt(c -> c.binding().displayOrder())
                .thenComparing(c -> c.binding().itemCode()))
            .orElseThrow(() -> new CheckinSafetyCatalogException(Code.INVALID_CONTRIBUTION,
                "Safety route selection requires a positive contribution"));
        return new Selection(
            winner.binding().itemCode(),
            route(winner.binding().routeCode(), version),
            winner.contribution().contribution(),
            winner.binding().displayOrder(),
            metadata.catalogVersion()
        );
    }

    private void requireVersion(String version) {
        if (!metadata.catalogVersion().equals(version)) {
            throw new CheckinSafetyCatalogException(Code.VERSION_NOT_FOUND,
                "Check-in safety catalog version " + version + " is unavailable");
        }
    }

    private static byte[] canonicalSource() {
        try {
            return WeightedCheckinDefinitionCatalog.canonicalSourceJson();
        } catch (IOException | IllegalArgumentException failure) {
            throw new CheckinSafetyCatalogException(Code.SOURCE_INVALID, "Cannot read canonical check-in source", failure);
        } catch (IllegalStateException failure) {
            throw new CheckinSafetyCatalogException(Code.SOURCE_MISSING, failure.getMessage(), failure);
        }
    }

    private record Parsed(List<RouteDefinition> routes, List<Binding> bindings, Metadata metadata) {}

    private record SourceItem(String code, CheckinPhase phase, int displayOrder, boolean scored, BigDecimal safetyWeight) {}

    private static Parsed parse(byte[] bytes) {
        String sha;
        JsonNode root;
        try {
            sha = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
            root = new ObjectMapper().readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new CheckinSafetyCatalogException(Code.SOURCE_INVALID, "Canonical weighted source is invalid JSON", failure);
        }
        if (root == null || !root.isObject() || !SOURCE_SCHEMA_VERSION.equals(string(root, "schema_version"))) {
            throw new CheckinSafetyCatalogException(Code.SOURCE_INVALID, "Unsupported check-in weighted source schema");
        }

        var sourceRoutes = array(root, "safety_route_catalog");
        var routes = new java.util.ArrayList<RouteDefinition>();
        for (var source : sourceRoutes) {
            String code = string(source, "code");
            routes.add(new RouteDefinition(
                code,
                string(source, "description"),
                "checkin.safety.route." + code.toLowerCase(Locale.ROOT) + ".v3",
                SafetyDisposition.REDIRECT,
                SafetySeverity.MEDIUM,
                Set.of(OPEN_SAFETY_CLARIFICATION),
                Set.of(OPEN_SAFETY_CLARIFICATION)
            ));
        }
        if (routes.size() != EXPECTED_ROUTE_COUNT) {
            throw new CheckinSafetyCatalogException(Code.SOURCE_INVALID, "Expected " + EXPECTED_ROUTE_COUNT + " check-in safety routes");
        }
        var routeCodes = routes.stream().map(RouteDefinition::code).collect(Collectors.toSet());
        if (routeCodes.size() != routes.size()) {
            throw new CheckinSafetyCatalogException(Code.DUPLICATE_ROUTE, "Check-in safety route codes must be unique");
        }

        var items = itemMetadata(root);
        var bindings = new java.util.ArrayList<Binding>();
        for (var source : array(root, "item_safety_routes")) {
            String itemCode = string(source, "item_code");
            String routeCode = string(source, "route_code");
            if (!routeCodes.contains(routeCode)) {
                throw new CheckinSafetyCatalogException(Code.UNKNOWN_ROUTE,
                    "Binding " + itemCode + " references unknown route " + routeCode);
            }
            var item = items.get(itemCode);
            if (item == null) {
                throw new CheckinSafetyCatalogException(Code.UNKNOWN_ITEM, "Binding references unknown item " + itemCode);
            }
            if (!item.scored() || item.safetyWeight().signum() <= 0) {
                throw new CheckinSafetyCatalogException(Code.ZERO_WEIGHT_BINDING, "Item " + itemCode + " cannot be safety-route bound");
            }
            bindings.add(new Binding(itemCode, routeCode, item.phase(), item.displayOrder(), item.safetyWeight()));
        }
        if (bindings.size() != EXPECTED_BINDING_COUNT) {
            throw new CheckinSafetyCatalogException(Code.SOURCE_INVALID, "Expected " + EXPECTED_BINDING_COUNT + " check-in safety bindings");
        }
        var bound = bindings.stream().map(Binding::itemCode).collect(Collectors.toSet());
        if (bound.size() != bindings.size()) {
            throw new CheckinSafetyCatalogException(Code.DUPLICATE_BINDING, "Each check-in safety item must bind to exactly one route");
        }
        var positive = items.values().stream()
            .filter(i -> i.scored() && i.safetyWeight().signum() > 0)
            .map(SourceItem::code)
            .collect(Collectors.toSet());
        if (!positive.equals(bound)) {
            throw new CheckinSafetyCatalogException(Code.INCOMPLETE_SAFETY_COVERAGE,
                "Every safety_w > 0 item must have exactly one route and zero-weight items must not be bound");
        }
        return new Parsed(
            List.copyOf(routes),
            List.copyOf(bindings),
            new Metadata(CATALOG_VERSION, RULE_VERSION, SOURCE_SCHEMA_VERSION, sha,
                CanonicalSafetyCatalog.REVIEW, routes.size(), bindings.size())
        );
    }

    private static Map<String, SourceItem> itemMetadata(JsonNode root) {
        var result = new LinkedHashMap<String, SourceItem>();
        for (var period : array(root, "periods")) {
            for (var phase : List.of(CheckinPhase.MORNING, CheckinPhase.EVENING)) {
                var field = phase == CheckinPhase.MORNING ? "morning_items" : "evening_items";
                var phaseItems = array(period, field);
                for (int index = 0; index < phaseItems.size(); index++) {
                    var item = phaseItems.get(index);
                    String code = string(item, "code");
                    if (result.containsKey(code)) {
                        throw new CheckinSafetyCatalogException(Code.SOURCE_INVALID, "Duplicate check-in item " + code);
                    }
                    var scored = item.get("scored");
                    if (scored == null || !scored.isBoolean()) {
                        throw new CheckinSafetyCatalogException(Code.SOURCE_INVALID, "scored is required for " + code);
                    }
                    var weight = item.get("safety_w");
                    BigDecimal safetyWeight;
                    if (weight == null || weight.isNull()) {
                        safetyWeight = BigDecimal.ZERO;
                    } else if (weight.isNumber()) {
                        safetyWeight = weight.decimalValue();
                    } else {
                        throw new CheckinSafetyCatalogException(Code.SOURCE_INVALID, "safety_w must be numeric for " + code);
                    }
                    result.put(code, new SourceItem(code, phase, index + 1, scored.booleanValue(), safetyWeight));
                }
            }
        }
        return result;
    }

    private static JsonNode array(JsonNode node, String name) {
        var value = node.get(name);
        if (value == null || !value.isArray()) {
            throw new CheckinSafetyCatalogException(Code.SOURCE_INVALID, name + " is required");
        }
        return value;
    }

    private static String string(JsonNode node, String name) {
        var value = node.get(name);
        if (value == null || !value.isValueNode() || value.isNull() || value.asText().isBlank()) {
            throw new CheckinSafetyCatalogException(Code.SOURCE_INVALID, name + " is required");
        }
        return value.asText();
    }

    private static <T> Map<String, T> index(List<T> values, java.util.function.Function<T, String> key) {
        var result = new LinkedHashMap<String, T>();
        var seen = new HashSet<String>();
        for (var value : values) {
            if (seen.add(key.apply(value))) result.put(key.apply(value), value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static String hex(byte[] bytes) {
        var out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append("%02x".formatted(value & 0xff));
        return out.toString();
    }
}
