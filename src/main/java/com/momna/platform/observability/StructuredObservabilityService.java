package com.momna.platform.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StructuredObservabilityService {
    private static final Set<String> SAFE_EXACT_KEYS = Set.of(
        "environment","role","port","metric","value","operation","outcome",
        "compatibility_bridge","decision_source","span_name"
    );
    private static final Set<String> SAFE_SUFFIXES = Set.of(
        "_id","_ref","_version","_code","_status","_outcome","_count",
        "_ms","_type","_source","_schema"
    );
    private static final Set<String> FORBIDDEN_FRAGMENTS = Set.of(
        "raw_","payload","message_body","diary_text","medical_file","medical_content",
        "prompt_text","prompt_body","provider_response","private_context","access_token",
        "refresh_token","credential","password","secret","signed_url","destination","email","phone"
    );
    private static final Set<String> METRIC_LABEL_KEYS = Set.of(
        "module","operation","outcome","error_category","channel","job_type",
        "provider","cache","status","result","queue","pool"
    );
    private static final Set<String> HIGH_CARDINALITY = Set.of(
        "user_id","request_id","trace_id","flow_instance_id","context_snapshot_id","subject_id"
    );

    private final Logger logger = LoggerFactory.getLogger("com.momna.telemetry");
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String,AtomicLong> counters = new ConcurrentHashMap<>();

    public StructuredObservabilityService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void emit(
        String eventName,
        String level,
        TelemetryContext context,
        Map<String,String> fields,
        TelemetryErrorCode error
    ) {
        requireTechnical(eventName, "eventName");
        var safeFields = fields == null ? Map.<String,String>of() : Map.copyOf(fields);
        validateFields(safeFields);

        var body = new TreeMap<String,Object>();
        body.put("schema_version", 1);
        body.put("event", eventName);
        body.put("level", level);
        body.put("occurred_at", Instant.now().toString());
        body.put("request_id", context.requestId());
        body.put("trace_id", context.traceId());
        body.put("source_module", context.sourceModule());
        if (context.userId() != null) body.put("user_id", context.userId());
        if (context.flowInstanceId() != null) body.put("flow_instance_id", context.flowInstanceId());
        if (context.contextSnapshotId() != null) body.put("context_snapshot_id", context.contextSnapshotId());
        body.put("versions", new TreeMap<>(context.versions()));
        body.put("fields", new TreeMap<>(safeFields));
        if (error != null) {
            body.put("error_code", error.name());
            body.put("error_category", error.category().name());
            body.put("retryable", error.retryable());
        }

        var rendered = json(body);
        switch (level) {
            case "DEBUG" -> logger.debug(rendered);
            case "WARN" -> logger.warn(rendered);
            case "ERROR" -> logger.error(rendered);
            default -> logger.info(rendered);
        }
    }

    public void increment(String metric) {
        requireTechnical(metric, "metric");
        counters.computeIfAbsent(metric, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void recordMetric(
        String metric,
        double value,
        TelemetryContext context,
        Map<String,String> labels
    ) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Metric value must be finite");
        validateLabels(labels == null ? Map.of() : labels);
        counters.computeIfAbsent(metric, ignored -> new AtomicLong()).incrementAndGet();

        var fields = new LinkedHashMap<String,String>();
        fields.put("metric", metric);
        fields.put("value", Double.toString(value));
        if (labels != null) labels.forEach((k,v) -> fields.put("label_" + k, v));
        emit("metric.sample", "DEBUG", context, fields, null);
    }

    public long recordedSamples(String metric) {
        var counter = counters.get(metric);
        return counter == null ? 0L : counter.get();
    }

    public void error(TelemetryErrorCode code, TelemetryContext context) {
        emit("legacy.error", "ERROR", context, Map.of(), code);
    }

    private void validateFields(Map<String,String> fields) {
        if (fields.size() > 32) throw new IllegalArgumentException("Too many telemetry fields");
        for (var entry : fields.entrySet()) {
            var key = entry.getKey().toLowerCase(Locale.ROOT);
            if (FORBIDDEN_FRAGMENTS.stream().anyMatch(key::contains)) {
                throw new IllegalArgumentException("Sensitive telemetry field is forbidden");
            }
            var registered = SAFE_EXACT_KEYS.contains(key)
                || key.startsWith("label_")
                || SAFE_SUFFIXES.stream().anyMatch(key::endsWith);
            if (!registered) throw new IllegalArgumentException("Unregistered telemetry field");
            var value = entry.getValue();
            if (value == null || !value.matches("[A-Za-z0-9_.:/+-]{1,512}")) {
                throw new IllegalArgumentException("Telemetry value is not a bounded technical value");
            }
            if (value.regionMatches(true, 0, "Bearer", 0, 6)
                || value.matches("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")
                || (value.contains("://") && value.toLowerCase(Locale.ROOT).contains("signature"))) {
                throw new IllegalArgumentException("Authorization material is forbidden");
            }
        }
    }

    private void validateLabels(Map<String,String> labels) {
        if (labels.size() > 6) throw new IllegalArgumentException("Too many metric labels");
        for (var entry : labels.entrySet()) {
            if (HIGH_CARDINALITY.contains(entry.getKey())) {
                throw new IllegalArgumentException("High-cardinality metric label is forbidden");
            }
            if (!METRIC_LABEL_KEYS.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unregistered metric label");
            }
            if (entry.getValue() == null || !entry.getValue().matches("[A-Za-z0-9_.:/-]{1,80}")) {
                throw new IllegalArgumentException("Invalid metric label value");
            }
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Telemetry serialization failed", failure);
        }
    }

    private void requireTechnical(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9_.:/-]{1,160}")) {
            throw new IllegalArgumentException(name + " must be a bounded technical identifier");
        }
    }
}
