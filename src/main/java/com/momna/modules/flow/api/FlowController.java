package com.momna.modules.flow.api;

import com.momna.modules.auth.application.AuthException;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.flow.application.FlowException;
import com.momna.modules.flow.application.UniversalFlowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flow-instances")
public class FlowController {
    private final UniversalFlowService flows;

    public FlowController(UniversalFlowService flows) {
        this.flows = flows;
    }

    @PostMapping
    public UniversalFlowService.FlowState start(
        Authentication authentication,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody StartFlowRequest request
    ) {
        requireIdempotencyKey(idempotencyKey);
        var actor = actor(authentication);
        var definitionKey = switch (request.flowType().toUpperCase(Locale.ROOT)) {
            case "LIFECYCLE_ROUTER" -> "lifecycle-router";
            case "PERIOD_ONBOARDING" -> {
                if (request.period() == null || request.period().isBlank()) {
                    throw new IllegalArgumentException("period is required");
                }
                yield "period-onboarding-" + request.period().trim().toLowerCase(Locale.ROOT);
            }
            default -> throw new IllegalArgumentException("Unsupported flow type");
        };

        return flows.start(
            actor.userId(),
            definitionKey,
            idempotencyKey,
            null,
            request.variant(),
            request.period(),
            null,
            request.launchRef()
        );
    }

    @GetMapping("/{instanceId}")
    public UniversalFlowService.FlowState resume(
        Authentication authentication,
        @PathVariable UUID instanceId
    ) {
        return flows.resume(actor(authentication).userId(), instanceId);
    }

    @PutMapping("/{instanceId}/answers/{fieldId}")
    public UniversalFlowService.FlowState answer(
        Authentication authentication,
        @PathVariable UUID instanceId,
        @PathVariable String fieldId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody SubmitAnswerRequest request
    ) {
        requireIdempotencyKey(idempotencyKey);
        var mode = request.mode() == null ? "SUBMIT" : request.mode().trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("SUBMIT") && !mode.equals("CHANGE")) {
            throw new IllegalArgumentException("Unsupported answer mode");
        }
        return flows.answer(
            actor(authentication).userId(),
            instanceId,
            fieldId,
            request.value(),
            mode.equals("CHANGE"),
            request.expectedDefinitionVersion(),
            request.expectedRevision(),
            idempotencyKey
        );
    }

    @PostMapping("/{instanceId}/steps/{stepId}/skip")
    public UniversalFlowService.FlowState skip(
        Authentication authentication,
        @PathVariable UUID instanceId,
        @PathVariable String stepId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody RevisionRequest request
    ) {
        requireIdempotencyKey(idempotencyKey);
        return flows.skip(
            actor(authentication).userId(),
            instanceId,
            stepId,
            request.expectedDefinitionVersion(),
            request.expectedRevision(),
            idempotencyKey
        );
    }

    @PostMapping("/{instanceId}/actions/{actionCode}")
    public UniversalFlowService.FlowState action(
        Authentication authentication,
        @PathVariable UUID instanceId,
        @PathVariable String actionCode,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody RevisionRequest request
    ) {
        requireIdempotencyKey(idempotencyKey);
        var action = actionCode.trim().toUpperCase(Locale.ROOT);
        if (!action.equals("BACK")) {
            throw new FlowException("INVALID_TRANSITION", "Unsupported server-issued action");
        }
        return flows.back(
            actor(authentication).userId(),
            instanceId,
            request.expectedDefinitionVersion(),
            request.expectedRevision(),
            idempotencyKey
        );
    }

    @PostMapping("/{instanceId}/complete")
    public UniversalFlowService.FlowResult complete(
        Authentication authentication,
        @PathVariable UUID instanceId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody RevisionRequest request
    ) {
        requireIdempotencyKey(idempotencyKey);
        return flows.complete(
            actor(authentication).userId(),
            instanceId,
            request.expectedDefinitionVersion(),
            request.expectedRevision(),
            idempotencyKey
        );
    }

    @GetMapping("/{instanceId}/result")
    public UniversalFlowService.FlowResult result(
        Authentication authentication,
        @PathVariable UUID instanceId
    ) {
        return flows.result(actor(authentication).userId(), instanceId);
    }

    private AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new AuthException("AUTH_REQUIRED", "Authentication required");
        }
        return actor;
    }

    private void requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 160) {
            throw new IllegalArgumentException("A valid Idempotency-Key is required");
        }
    }

    public record StartFlowRequest(
        @NotBlank String flowType,
        String period,
        String launchRef,
        String variant
    ) {}

    public record SubmitAnswerRequest(
        @NotNull Map<String, Object> value,
        String mode,
        @NotNull Integer expectedDefinitionVersion,
        @NotNull Long expectedRevision
    ) {}

    public record RevisionRequest(
        @NotNull Integer expectedDefinitionVersion,
        @NotNull Long expectedRevision,
        String manualPeriod
    ) {}
}
