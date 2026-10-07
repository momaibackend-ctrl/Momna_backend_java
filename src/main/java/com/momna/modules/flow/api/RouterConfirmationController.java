package com.momna.modules.flow.api;

import com.momna.modules.auth.application.AuthException;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.flow.application.RouterConfirmationService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flow-instances")
public class RouterConfirmationController {
    private final RouterConfirmationService confirmation;

    public RouterConfirmationController(
        RouterConfirmationService confirmation
    ) {
        this.confirmation = confirmation;
    }

    @PostMapping("/{instanceId}/router/confirm")
    public RouterConfirmationService.RouterConfirmation confirm(
        Authentication authentication,
        @PathVariable UUID instanceId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody(required = false) RouterConfirmRequest request
    ) {
        requireIdempotencyKey(idempotencyKey);
        return confirmation.confirm(
            actor(authentication).userId(),
            instanceId,
            request == null ? null : request.manualPeriod(),
            idempotencyKey
        );
    }

    private AuthenticatedActor actor(
        Authentication authentication
    ) {
        if (
            authentication == null
                || !(authentication.getPrincipal()
                    instanceof AuthenticatedActor actor)
        ) {
            throw new AuthException(
                "AUTH_REQUIRED",
                "Authentication required"
            );
        }
        return actor;
    }

    private void requireIdempotencyKey(String value) {
        if (
            value == null
                || value.isBlank()
                || value.length() > 160
        ) {
            throw new IllegalArgumentException(
                "A valid Idempotency-Key is required"
            );
        }
    }

    public record RouterConfirmRequest(String manualPeriod) {}
}
