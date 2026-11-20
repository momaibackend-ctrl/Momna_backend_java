package com.momna.modules.checkin.safety;

import com.momna.modules.checkin.application.CheckinException;
import com.momna.modules.checkin.definition.CheckinSafetyRouteCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable one-step clarification flow. Option values map to server-owned safety signals;
 * client values are never interpreted as signals. The clarification id is deterministic:
 * sha256(sessionId|routeCode|decisionVersion).
 */
public final class CheckinSafetyClarificationFlow {
    private static final Pattern VALUE = Pattern.compile("[a-z0-9._-]{1,80}");
    private static final Pattern KEY = Pattern.compile("[a-z0-9._-]{3,160}");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,79}");

    public record Option(String value, String labelKey, Set<String> signalKeys) {
        public Option {
            require(VALUE.matcher(value).matches(), "Invalid clarification option value");
            require(KEY.matcher(labelKey).matches(), "Invalid clarification label key");
            signalKeys = Collections.unmodifiableSet(new LinkedHashSet<>(signalKeys));
            require(signalKeys.stream().allMatch(s -> CODE.matcher(s).matches()), "Invalid safety signal key");
        }
    }

    public record Definition(String routeCode, String presentationKey, List<Option> options) {
        public Definition {
            require(CODE.matcher(routeCode).matches(), "Invalid safety route code");
            require(KEY.matcher(presentationKey).matches(), "Invalid presentation key");
            options = List.copyOf(options);
            require(!options.isEmpty(), "Clarification requires options");
            require(options.stream().map(Option::value).distinct().count() == options.size(),
                "Clarification option values must be unique");
        }
    }

    private final Map<String, Definition> byRoute;

    public CheckinSafetyClarificationFlow(List<Definition> definitions) {
        require(!definitions.isEmpty(), "Clarification definitions are required");
        var map = new LinkedHashMap<String, Definition>();
        for (var definition : definitions) map.put(definition.routeCode(), definition);
        require(map.size() == definitions.size(), "Clarification routes must be unique");
        this.byRoute = Collections.unmodifiableMap(map);
    }

    /** Every check-in route asks one question: safe, or urgent clinical risk. */
    public static CheckinSafetyClarificationFlow standard(CheckinSafetyRouteCatalog catalog) {
        return new CheckinSafetyClarificationFlow(catalog.routes().stream()
            .map(route -> new Definition(route.code(), route.contentRef(), List.of(
                new Option("safe", "checkin.safety.safe", Set.of()),
                new Option("urgent", "checkin.safety.urgent", Set.of("URGENT_CLINICAL_RISK"))
            )))
            .toList());
    }

    public Map<String, Object> open(String sessionId, String routeCode, String decisionVersion, String presentationKey) {
        var definition = definition(routeCode);
        if (!definition.presentationKey().equals(presentationKey)) {
            throw new CheckinException("SAFETY_ROUTE_VERSION_MISMATCH", "Safety route content version mismatch");
        }
        var options = new java.util.ArrayList<Map<String, Object>>();
        for (var option : definition.options()) {
            var item = new LinkedHashMap<String, Object>();
            item.put("value", option.value());
            item.put("labelKey", option.labelKey());
            options.add(item);
        }
        var view = new LinkedHashMap<String, Object>();
        view.put("clarificationId", clarificationId(sessionId, routeCode, decisionVersion));
        view.put("presentationKey", definition.presentationKey());
        view.put("options", List.copyOf(options));
        return view;
    }

    public Set<String> answer(
        String sessionId, String routeCode, String decisionVersion, String clarificationId, String value
    ) {
        var definition = definition(routeCode);
        if (!clarificationId(sessionId, routeCode, decisionVersion).equals(clarificationId)) {
            throw new CheckinException("SAFETY_CLARIFICATION_NOT_CURRENT",
                "No current safety clarification matches the request");
        }
        return definition.options().stream()
            .filter(option -> option.value().equals(value))
            .findFirst()
            .map(Option::signalKeys)
            .orElseThrow(() -> new CheckinException("SAFETY_VALUE_INVALID", "Unknown safety clarification option"));
    }

    public static String clarificationId(String sessionId, String routeCode, String decisionVersion) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest((sessionId + "|" + routeCode + "|" + decisionVersion).getBytes(StandardCharsets.UTF_8));
            var result = new StringBuilder(64);
            for (byte b : digest) result.append("%02x".formatted(b & 0xff));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private Definition definition(String routeCode) {
        var definition = byRoute.get(routeCode);
        if (definition == null) {
            throw new CheckinException("SAFETY_ROUTE_VERSION_MISMATCH", "Safety route has no clarification");
        }
        return definition;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
