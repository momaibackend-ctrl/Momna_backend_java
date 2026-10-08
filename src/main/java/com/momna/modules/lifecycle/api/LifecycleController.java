package com.momna.modules.lifecycle.api;

import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.lifecycle.application.LifecycleQueryService;
import com.momna.modules.lifecycle.domain.*;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/lifecycle")
public class LifecycleController {
    private final LifecycleQueryService lifecycle;

    public LifecycleController(LifecycleQueryService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @GetMapping
    public LifecycleSnapshotResponse current(Authentication authentication) {
        var snapshot = lifecycle.current(actor(authentication).userId(), Instant.now());
        return new LifecycleSnapshotResponse(
            snapshot.primary() == null ? null : entry(snapshot.primary()),
            snapshot.contexts().stream().map(this::context).toList()
        );
    }

    @GetMapping("/history")
    public PagedLifecycle history(
        Authentication authentication,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        validatePage(limit, offset);
        var all = lifecycle.history(actor(authentication).userId());
        var items = all.stream().skip(offset).limit(limit).map(this::entry).toList();
        Integer nextOffset = offset + items.size() < all.size() ? offset + items.size() : null;
        return new PagedLifecycle(items, nextOffset);
    }

    private AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new com.momna.modules.auth.application.AuthException("AUTH_REQUIRED", "Authentication required");
        }
        return actor;
    }

    private LifecycleEntryResponse entry(LifecycleEntry value) {
        return new LifecycleEntryResponse(
            value.period().name(),
            value.substage(),
            value.effectiveFrom(),
            value.effectiveTo(),
            value.confidence(),
            value.selectedManually()
        );
    }

    private LifecycleContextResponse context(LifecycleContext value) {
        return new LifecycleContextResponse(
            value.contextType(), value.validFrom(), value.validTo(), value.confidence()
        );
    }

    private void validatePage(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }
    }

    public record LifecycleSnapshotResponse(
        LifecycleEntryResponse primary,
        List<LifecycleContextResponse> contexts
    ) {}

    public record LifecycleEntryResponse(
        String period,
        String substage,
        Instant effectiveFrom,
        Instant effectiveTo,
        double confidence,
        boolean selectedManually
    ) {}

    public record LifecycleContextResponse(
        String contextType,
        Instant validFrom,
        Instant validTo,
        double confidence
    ) {}

    public record PagedLifecycle(List<LifecycleEntryResponse> items, Integer nextOffset) {}
}
