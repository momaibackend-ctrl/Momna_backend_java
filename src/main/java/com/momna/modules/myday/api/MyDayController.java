package com.momna.modules.myday.api;

import com.momna.modules.auth.application.AuthException;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.myday.application.MyDayApplicationService;
import com.momna.modules.myday.application.MyDayViews.MyDayView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/my-day")
public class MyDayController {
    private final MyDayApplicationService myDay;

    public MyDayController(MyDayApplicationService myDay) {
        this.myDay = myDay;
    }

    @PostMapping("/generate")
    public ResponseEntity<MyDayView> generate(Authentication authentication) {
        var result = myDay.generate(actor(authentication).userId());
        return "CREATED".equals(result.outcome())
            ? ResponseEntity.status(201).body(result.myDay())
            : ResponseEntity.ok(result.myDay());
    }

    @GetMapping("/today")
    public MyDayView today(Authentication authentication) {
        return myDay.today(actor(authentication).userId());
    }

    @GetMapping("/{myDayId}")
    public MyDayView get(Authentication authentication, @PathVariable String myDayId) {
        return myDay.get(actor(authentication).userId(), myDayId);
    }

    @PatchMapping("/{myDayId}/cards/{cardId}")
    public MyDayView updateCard(
        Authentication authentication,
        @PathVariable String myDayId,
        @PathVariable String cardId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CardProgressRequest request
    ) {
        return myDay.updateCard(actor(authentication).userId(), myDayId, cardId,
            request.expectedRevision(), request.status(), idempotencyKey);
    }

    public record CardProgressRequest(@NotNull Long expectedRevision, @NotBlank String status) {}

    private AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new AuthException("AUTH_REQUIRED", "Authentication is required");
        }
        return actor;
    }
}
