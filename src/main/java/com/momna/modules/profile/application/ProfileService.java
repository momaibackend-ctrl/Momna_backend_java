package com.momna.modules.profile.application;

import com.momna.modules.profile.domain.ConsentState;
import com.momna.modules.profile.infrastructure.*;
import com.momna.shared.outbox.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserProfileRepository profiles;
    private final ConsentRecordRepository consents;
    private final OutboxEventRepository outbox;
    private final Clock clock = Clock.systemUTC();

    public ProfileService(
        UserProfileRepository profiles,
        ConsentRecordRepository consents,
        OutboxEventRepository outbox
    ) {
        this.profiles = profiles;
        this.consents = consents;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public UserProfileEntity get(String userId) {
        requireUserId(userId);
        return profiles.findById(userId).orElse(null);
    }

    @Transactional
    public UserProfileEntity updatePreferences(
        String userId,
        String measurementSystem,
        Map<String, String> unitPreferences,
        Map<String, Boolean> notificationPreferences,
        Long expectedVersion
    ) {
        var profile = ensureProfile(userId);
        checkVersion(profile, expectedVersion);
        var now = clock.instant();
        profile.updatePreferences(
            measurementSystem,
            unitPreferences == null ? Map.of() : unitPreferences,
            notificationPreferences == null ? Map.of() : notificationPreferences,
            now
        );
        var saved = profiles.saveAndFlush(profile);
        publishProfileChanged(userId, "preferences", saved.getVersion(), now);
        return saved;
    }

    @Transactional
    public UserProfileEntity updateLocalization(
        String userId,
        String preferredLanguage,
        String locale,
        String countryRegion,
        String timezone,
        Long expectedVersion
    ) {
        requireText(preferredLanguage, "preferredLanguage");
        requireText(locale, "locale");
        requireText(countryRegion, "countryRegion");
        requireText(timezone, "timezone");
        if (!locale.matches("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")) {
            throw new IllegalArgumentException("Unsupported locale");
        }
        ZoneId.of(timezone);

        var profile = ensureProfile(userId);
        checkVersion(profile, expectedVersion);
        var now = clock.instant();
        profile.updateLocalization(preferredLanguage, locale, countryRegion, timezone, now);
        var saved = profiles.saveAndFlush(profile);
        publishProfileChanged(userId, "locale-country-timezone", saved.getVersion(), now);
        return saved;
    }

    @Transactional(readOnly = true)
    public java.util.List<ConsentRecordEntity> listConsents(String userId) {
        requireUserId(userId);
        return consents.findByUserIdOrderByRecordedAtAscIdAsc(userId);
    }

    @Transactional
    public ConsentRecordEntity grant(String userId, String consentType, String policyVersion, String source) {
        return appendConsent(userId, consentType, policyVersion, source, ConsentState.GRANTED);
    }

    @Transactional
    public ConsentRecordEntity withdraw(String userId, String consentType, String policyVersion, String source) {
        return appendConsent(userId, consentType, policyVersion, source, ConsentState.WITHDRAWN);
    }

    private ConsentRecordEntity appendConsent(
        String userId, String consentType, String policyVersion, String source, ConsentState state
    ) {
        ensureProfile(userId);
        requireText(consentType, "consentType");
        requireText(policyVersion, "policyVersion");
        requireText(source, "source");

        var history = consents.findByUserIdOrderByRecordedAtAscIdAsc(userId);
        var latest = history.stream()
            .filter(x -> x.getConsentType().equals(consentType) && x.getPolicyVersion().equals(policyVersion))
            .reduce((first, second) -> second)
            .orElse(null);
        if (latest != null && latest.getState() == state) return latest;

        var now = clock.instant();
        var record = new ConsentRecordEntity(
            UUID.randomUUID().toString(), userId, consentType, policyVersion, state, now, source
        );
        consents.save(record);
        outbox.save(new OutboxEventEntity(
            "consent-" + UUID.randomUUID(),
            "identity.consent.changed",
            now,
            Map.of(
                "schemaVersion", 1,
                "userId", userId,
                "consentType", consentType,
                "policyVersion", policyVersion,
                "state", state.name(),
                "source", source,
                "recordId", record.getId()
            )
        ));
        return record;
    }

    private UserProfileEntity ensureProfile(String userId) {
        requireUserId(userId);
        return profiles.findById(userId)
            .orElseGet(() -> profiles.save(new UserProfileEntity(userId, clock.instant())));
    }

    private void publishProfileChanged(String userId, String area, long version, Instant at) {
        outbox.save(new OutboxEventEntity(
            "profile-" + UUID.randomUUID(),
            "identity.profile.changed",
            at,
            Map.of("schemaVersion", 1, "userId", userId, "changedArea", area, "version", version)
        ));
    }

    private void checkVersion(UserProfileEntity profile, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion.longValue() != profile.getVersion()) {
            throw new IllegalStateException("Profile version conflict");
        }
    }

    private void requireUserId(String userId) {
        requireText(userId, "userId");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
