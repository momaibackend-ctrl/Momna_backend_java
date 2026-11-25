package com.momna.modules.flow.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an onboarding answer may be (ONB-API-01, MOMNA-1024): the field's data type and the machine values of its
 * canonical registry entry. The flow returns it with the current step, so the client never guesses a value, and
 * checks every answer against it ({@code INVALID_FIELD_VALUE}, 422).
 *
 * <p>An answer arrives as {@code {"kind": .., "value": x}} or with x directly under {@code selected},
 * {@code date} or {@code by_child}. By type:
 * <ul>
 *   <li>ENUM: x is one of the allowed values, or, per child, a map from child to an allowed value;</li>
 *   <li>OBJECT with allowed keys: x is a list of allowed keys, or a map of allowed keys holding every required
 *       key;</li>
 *   <li>OBJECT with allowed values per child: a map from child to an allowed value;</li>
 *   <li>DATE: an ISO date, within the field's range around today;</li>
 *   <li>NUMBER: a number within the field's range;</li>
 *   <li>TEXT: a string no longer than the field's maximum, of the field's form when it has one.</li>
 * </ul>
 * A field without a registry entry or without listed values accepts any value of its shape.
 */
public record FlowAnswerSpec(
    String dataType,
    List<String> options,
    List<String> requiredKeys,
    /** The choice codes of each key of a one-choice-per-group answer ("How do you prefer to receive support?"). */
    Map<String, List<String>> valuesByKey,
    boolean perChild,
    Integer maxLength,
    String pattern,
    BigDecimal minimum,
    BigDecimal maximum,
    Integer minDaysFromNow,
    Integer maxDaysFromNow,
    String earliest
) {
    public static final FlowAnswerSpec UNKNOWN = new FlowAnswerSpec(null, null, null, null, false, null, null, null,
        null, null, null, null);

    @SuppressWarnings("unchecked")
    public static FlowAnswerSpec of(String dataType, Map<String, Object> schema) {
        if (schema == null) schema = Map.of();
        var values = schema.get("allowedValues") instanceof List<?> v ? strings(v)
            : schema.get("allowedKeys") instanceof List<?> k ? strings(k) : null;
        return new FlowAnswerSpec(
            dataType,
            values,
            schema.get("requiredKeys") instanceof List<?> r ? strings(r) : null,
            schema.get("valuesByKey") instanceof Map<?, ?> byKey ? choices(byKey) : null,
            Boolean.TRUE.equals(schema.get("perChild")),
            schema.get("maxLength") instanceof Number n ? n.intValue() : null,
            schema.get("pattern") instanceof String p ? p : null,
            decimal(schema.get("minimum")),
            decimal(schema.get("maximum")),
            schema.get("minDaysFromNow") instanceof Number n ? n.intValue() : null,
            schema.get("maxDaysFromNow") instanceof Number n ? n.intValue() : null,
            schema.get("earliest") instanceof String s ? s : null);
    }

    /** Throws {@code VALIDATION} when the answer is not one this field accepts. */
    public void validate(Map<String, Object> payload) {
        if (dataType == null) return;
        var x = inner(payload);
        switch (dataType) {
            case "ENUM" -> {
                if (perChild && x instanceof Map<?, ?> byChild) {
                    for (var value : byChild.values()) requireOption(value);
                } else {
                    requireOption(x);
                }
            }
            case "OBJECT" -> validateObject(x);
            case "DATE" -> validateDate(x);
            case "NUMBER" -> validateNumber(x);
            case "TEXT" -> {
                if (!(x instanceof String text)) throw invalid("a text is required");
                if (maxLength != null && text.length() > maxLength) throw invalid("the text is too long");
                if (pattern != null && !text.matches(pattern)) throw invalid("the text has the wrong form");
            }
            default -> {}
        }
    }

    private void validateObject(Object x) {
        if (options == null) return;
        if (perChild) {
            if (!(x instanceof Map<?, ?> byChild) || byChild.isEmpty()) throw invalid("an answer per child is required");
            for (var value : byChild.values()) requireOption(value);
            return;
        }
        if (x instanceof Collection<?> selected) {
            if (selected.isEmpty()) throw invalid("at least one option is required");
            for (var value : selected) requireOption(value);
            if (requiredKeys != null && !requiredKeys.isEmpty()) throw invalid("every required key needs an answer");
            return;
        }
        if (x instanceof Map<?, ?> keyed) {
            if (keyed.isEmpty()) throw invalid("at least one option is required");
            for (var key : keyed.keySet()) requireOption(key);
            if (valuesByKey != null) {
                for (var entry : keyed.entrySet()) {
                    var allowed = valuesByKey.get(String.valueOf(entry.getKey()));
                    if (allowed != null && !allowed.contains(String.valueOf(entry.getValue()))) {
                        throw invalid("not a choice of " + entry.getKey());
                    }
                }
            }
            if (requiredKeys != null) {
                for (var key : requiredKeys) {
                    if (!keyed.containsKey(key)) throw invalid("every required key needs an answer");
                }
            }
            return;
        }
        if (x instanceof String single && requiredKeys == null) {
            requireOption(single);
            return;
        }
        throw invalid("a list of options is required");
    }

    private void validateDate(Object x) {
        if (!(x instanceof String text) || text.length() < 10) throw invalid("a date is required");
        LocalDate date;
        try {
            date = LocalDate.parse(text.substring(0, 10));
        } catch (RuntimeException notADate) {
            throw invalid("a date is required");
        }
        // The user's local today is within a day of UTC's.
        var today = LocalDate.now(ZoneOffset.UTC);
        if (minDaysFromNow != null && date.isBefore(today.plusDays(minDaysFromNow - 1L))) throw invalid("date out of range");
        if (maxDaysFromNow != null && date.isAfter(today.plusDays(maxDaysFromNow + 1L))) throw invalid("date out of range");
        if (earliest != null) {
            try {
                if (date.isBefore(LocalDate.parse(earliest))) throw invalid("date out of range");
            } catch (java.time.format.DateTimeParseException ignored) {
                // A non-date bound is descriptive only.
            }
        }
    }

    private void validateNumber(Object x) {
        BigDecimal number;
        try {
            number = x instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(String.valueOf(x));
        } catch (NumberFormatException notANumber) {
            throw invalid("a number is required");
        }
        if (minimum != null && number.compareTo(minimum) < 0) throw invalid("number out of range");
        if (maximum != null && number.compareTo(maximum) > 0) throw invalid("number out of range");
    }

    private void requireOption(Object value) {
        if (options == null) return;
        if (!(value instanceof String code) || !options.contains(code)) throw invalid("not an option of this field");
    }

    static Object inner(Map<String, Object> payload) {
        if (payload == null) return null;
        for (var key : List.of("value", "selected", "date", "by_child")) {
            if (payload.containsKey(key)) return payload.get(key);
        }
        return payload.keySet().equals(Set.of("kind")) ? null : payload;
    }

    private static FlowException invalid(String reason) {
        return new FlowException("VALIDATION", "Invalid answer: " + reason);
    }

    private static Map<String, List<String>> choices(Map<?, ?> byKey) {
        var out = new java.util.LinkedHashMap<String, List<String>>();
        byKey.forEach((key, values) -> {
            if (values instanceof List<?> list) out.put(String.valueOf(key), strings(list));
        });
        return java.util.Collections.unmodifiableMap(out);
    }

    private static List<String> strings(List<?> values) {
        return values.stream().map(String::valueOf).toList();
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof Number n ? new BigDecimal(n.toString()) : null;
    }
}
