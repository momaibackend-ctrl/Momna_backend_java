package com.momna.modules.flow.application;

import com.momna.core.fields.application.CanonicalFieldRegistryService;
import com.momna.modules.lifecycle.application.LifecycleCommandService;
import com.momna.modules.lifecycle.application.LifecycleQueryService;
import com.momna.modules.lifecycle.domain.LifecyclePeriod;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouterConfirmationService {
    private final UniversalFlowService flows;
    private final LifecycleQueryService lifecycleQuery;
    private final LifecycleCommandService lifecycleCommands;
    private final CanonicalFieldRegistryService fields;

    public RouterConfirmationService(
        UniversalFlowService flows,
        LifecycleQueryService lifecycleQuery,
        LifecycleCommandService lifecycleCommands,
        CanonicalFieldRegistryService fields
    ) {
        this.flows = flows;
        this.lifecycleQuery = lifecycleQuery;
        this.lifecycleCommands = lifecycleCommands;
        this.fields = fields;
    }

    @Transactional
    public RouterConfirmation confirm(
        String userId,
        UUID instanceId,
        String manualPeriod,
        String operationId
    ) {
        var result = flows.result(userId, instanceId);
        if (!"lifecycle-router".equals(result.definitionKey())) {
            throw new IllegalArgumentException("not a router flow");
        }
        if (!result.completed()) {
            throw new FlowException(
                "INVALID_TRANSITION",
                "Router flow must be completed before confirmation"
            );
        }

        var decision = RouterRoutingTable.route(
            scalarAnswers(result.answers())
        );

        var selected = manualPeriod == null
            ? decision.period()
            : parsePeriod(manualPeriod);
        var manually = manualPeriod != null;

        var transitionScopeId = UUID.nameUUIDFromBytes(
            (instanceId + "|" + operationId)
                .getBytes(StandardCharsets.UTF_8)
        ).toString();

        result.answers().forEach((fieldId, payload) ->
            fields.appendFlowTransitionValue(
                userId,
                fieldId,
                payload,
                result.definitionKey(),
                instanceId.toString(),
                transitionScopeId
            )
        );

        var current = lifecycleQuery.current(
            userId,
            Instant.now()
        );

        if (
            current.primary() == null
                || current.primary().period() != selected
        ) {
            var confidence = manually
                ? 1.0
                : confidence(decision.confidence());

            var transition = lifecycleCommands.proposeTransition(
                userId,
                selected,
                manually
                    ? "MANUAL_SELECTION"
                    : decision.substage(),
                manually
                    ? "ONBOARDING_ROUTER_MANUAL"
                    : "ONBOARDING_ROUTER",
                "router-v1",
                "FLOW",
                confidence,
                manually,
                operationId,
                current.primary() == null
                    ? null
                    : current.primary().id()
            );
            lifecycleCommands.confirmTransition(
                userId,
                transition.getId()
            );
        }

        var refreshed = lifecycleQuery.current(
            userId,
            Instant.now()
        );
        for (var type : decision.additionalContexts()) {
            var exists = refreshed.contexts().stream()
                .anyMatch(context ->
                    context.contextType().equals(type)
                );
            if (!exists) {
                lifecycleCommands.addContext(
                    userId,
                    type,
                    Instant.now(),
                    null,
                    "FLOW",
                    confidence(decision.confidence())
                );
            }
        }

        var periodFlow = flows.start(
            userId,
            "period-onboarding-"
                + selected.name().toLowerCase(Locale.ROOT),
            "period-" + operationId,
            null,
            null,
            selected.name(),
            null,
            transitionScopeId
        );

        return new RouterConfirmation(
            instanceId,
            selected.name(),
            manually,
            periodFlow
        );
    }

    private Map<String, String> scalarAnswers(
        Map<String, Map<String, Object>> answers
    ) {
        var result = new LinkedHashMap<String, String>();
        answers.forEach((field, payload) -> {
            var scalar = scalar(payload);
            if (scalar != null) result.put(field, scalar);
        });
        return Map.copyOf(result);
    }

    private String scalar(Map<String, Object> payload) {
        if (payload == null) return null;
        var value = payload.get("value");

        if (value instanceof Map<?, ?> nested) {
            value = nested.get("value");
        }
        if (
            value instanceof String
                || value instanceof Number
                || value instanceof Boolean
        ) {
            return String.valueOf(value);
        }
        return null;
    }

    private LifecyclePeriod parsePeriod(String value) {
        try {
            return LifecyclePeriod.valueOf(
                value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(
                "unsupported period"
            );
        }
    }

    private double confidence(
        RouterRoutingTable.Confidence value
    ) {
        return switch (value) {
            case HIGH -> 1.0;
            case MEDIUM -> 0.7;
            case LOW -> 0.4;
        };
    }

    public record RouterConfirmation(
        UUID routerInstanceId,
        String selectedPeriod,
        boolean selectedManually,
        UniversalFlowService.FlowState periodFlow
    ) {}
}
