package com.momna.modules.profile.api;

import com.momna.modules.auth.application.AuthException;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.profile.application.ProfileService;
import com.momna.modules.profile.infrastructure.ConsentRecordEntity;
import com.momna.modules.profile.infrastructure.UserProfileEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profile")
    public ProfileResponse profile(Authentication authentication) {
        var profile = profiles.get(actor(authentication).userId());
        if (profile == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        return profile(profile);
    }

    @PatchMapping("/profile/preferences")
    public ProfileResponse updatePreferences(
        Authentication authentication,
        @Valid @RequestBody PreferencesUpdate request
    ) {
        if (request.expectedVersion() != null && request.expectedVersion() < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
        return profile(profiles.updatePreferences(
            actor(authentication).userId(),
            request.measurementSystem(),
            request.unitPreferences() == null ? Map.of() : request.unitPreferences(),
            request.notificationPreferences() == null ? Map.of() : request.notificationPreferences(),
            request.expectedVersion()
        ));
    }

    @PatchMapping("/profile/localization")
    public ProfileResponse updateLocalization(
        Authentication authentication,
        @Valid @RequestBody LocalizationUpdate request
    ) {
        if (request.expectedVersion() != null && request.expectedVersion() < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
        return profile(profiles.updateLocalization(
            actor(authentication).userId(),
            request.preferredLanguage(),
            request.locale(),
            request.countryRegion(),
            request.timezone(),
            request.expectedVersion()
        ));
    }

    @GetMapping("/consents")
    public PagedConsents consents(
        Authentication authentication,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        validatePage(limit, offset);
        var all = profiles.listConsents(actor(authentication).userId());
        var items = all.stream().skip(offset).limit(limit).map(this::consent).toList();
        Integer nextOffset = offset + items.size() < all.size() ? offset + items.size() : null;
        return new PagedConsents(items, nextOffset);
    }

    @PostMapping("/consents/{consentType}/grant")
    public ConsentResponse grant(
        Authentication authentication,
        @PathVariable String consentType,
        @Valid @RequestBody ConsentChange request
    ) {
        return consent(profiles.grant(
            actor(authentication).userId(), consentType, request.policyVersion(), "CLIENT_API"
        ));
    }

    @PostMapping("/consents/{consentType}/withdraw")
    public ConsentResponse withdraw(
        Authentication authentication,
        @PathVariable String consentType,
        @Valid @RequestBody ConsentChange request
    ) {
        return consent(profiles.withdraw(
            actor(authentication).userId(), consentType, request.policyVersion(), "CLIENT_API"
        ));
    }

    private AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new AuthException("AUTH_REQUIRED", "Authentication required");
        }
        return actor;
    }

    private ProfileResponse profile(UserProfileEntity value) {
        return new ProfileResponse(
            value.getDisplayName(),
            value.getBirthDate() == null ? null : value.getBirthDate().toString(),
            value.getPreferredLanguage(),
            value.getLocale(),
            value.getCountryRegion(),
            value.getTimezone(),
            value.getAccountStatus().name(),
            value.getVersion(),
            new PreferencesResponse(
                value.getMeasurementSystem(),
                value.getUnitPreferences(),
                value.getNotificationPreferences()
            )
        );
    }

    private ConsentResponse consent(ConsentRecordEntity value) {
        return new ConsentResponse(
            value.getConsentType(),
            value.getPolicyVersion(),
            value.getState().name(),
            value.getRecordedAt()
        );
    }

    private void validatePage(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }
    }

    public record PreferencesUpdate(
        String measurementSystem,
        Map<String, String> unitPreferences,
        Map<String, Boolean> notificationPreferences,
        Long expectedVersion
    ) {}

    public record LocalizationUpdate(
        @NotBlank String preferredLanguage,
        @NotBlank String locale,
        @NotBlank String countryRegion,
        @NotBlank String timezone,
        Long expectedVersion
    ) {}

    public record ConsentChange(@NotBlank String policyVersion) {}

    public record ProfileResponse(
        String displayName,
        String birthDate,
        String preferredLanguage,
        String locale,
        String countryRegion,
        String timezone,
        String accountStatus,
        long version,
        PreferencesResponse preferences
    ) {}

    public record PreferencesResponse(
        String measurementSystem,
        Map<String, String> unitPreferences,
        Map<String, Boolean> notificationPreferences
    ) {}

    public record ConsentResponse(
        String consentType,
        String policyVersion,
        String state,
        Instant recordedAt
    ) {}

    public record PagedConsents(List<ConsentResponse> items, Integer nextOffset) {}
}
