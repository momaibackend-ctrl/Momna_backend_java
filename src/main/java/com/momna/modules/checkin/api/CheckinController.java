package com.momna.modules.checkin.api;

import com.momna.modules.auth.application.AuthException;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.checkin.application.CheckinApplicationService;
import com.momna.modules.checkin.application.CheckinViews.*;
import com.momna.modules.checkin.domain.CheckinPhase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/check-in")
public class CheckinController {
    private final CheckinApplicationService checkin;

    public CheckinController(CheckinApplicationService checkin) {
        this.checkin = checkin;
    }

    @GetMapping("/today")
    public TodayView today(Authentication authentication) {
        return checkin.today(actor(authentication).userId());
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionView> startOrResume(
        Authentication authentication,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody StartRequest request
    ) {
        var phase = phase(request.phase());
        var result = checkin.startOrResume(actor(authentication).userId(), phase, idempotencyKey);
        return result.created()
            ? ResponseEntity.status(201).body(result.session())
            : ResponseEntity.ok(result.session());
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionView session(
        Authentication authentication,
        @PathVariable String sessionId
    ) {
        return checkin.session(actor(authentication).userId(), sessionId);
    }

    @PatchMapping("/sessions/{sessionId}")
    public SessionView patch(
        Authentication authentication,
        @PathVariable String sessionId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PatchRequest request
    ) {
        var changes = request.answerChanges().stream()
            .map(x -> new AnswerChange(x.itemCode(), x.value()))
            .toList();
        return checkin.patch(
            actor(authentication).userId(),
            sessionId,
            request.expectedRevision(),
            changes,
            idempotencyKey
        );
    }

    @PostMapping("/sessions/{sessionId}/submit")
    public FinalizeResult submit(
        Authentication authentication,
        @PathVariable String sessionId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody SubmitRequest request
    ) {
        return checkin.submit(
            actor(authentication).userId(),
            sessionId,
            request.expectedRevision(),
            idempotencyKey
        );
    }

    private AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new AuthException("AUTH_REQUIRED", "Authentication required");
        }
        return actor;
    }

    private CheckinPhase phase(String value) {
        try {
            return CheckinPhase.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Unsupported phase");
        }
    }

    public record StartRequest(@NotBlank String phase) {}

    public record AnswerChangeRequest(
        @NotBlank String itemCode,
        Integer value
    ) {}

    public record PatchRequest(
        @NotNull Long expectedRevision,
        @NotNull List<@Valid AnswerChangeRequest> answerChanges
    ) {}

    public record SubmitRequest(@NotNull Long expectedRevision) {}
}
