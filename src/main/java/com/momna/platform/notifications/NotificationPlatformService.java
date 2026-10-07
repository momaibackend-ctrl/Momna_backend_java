package com.momna.platform.notifications;

import com.momna.modules.profile.application.ProfileService;
import com.momna.platform.jobs.*;
import com.momna.platform.localization.*;
import com.momna.platform.notifications.infrastructure.*;
import com.momna.platform.safety.CanonicalSafetyService;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPlatformService {
    private final NotificationScheduleRepository schedules;
    private final NotificationPreferenceRepository preferences;
    private final NotificationDeliveryAttemptRepository attempts;
    private final NotificationOperationRepository operations;
    private final ProfileService profiles;
    private final CanonicalTimeService time;
    private final CanonicalSafetyService safety;
    private final DurableJobQueue jobs;
    private final ObjectProvider<NotificationContentResolver> contentResolvers;
    private final ObjectProvider<NotificationConsentGuard> consentGuards;
    private final Map<NotificationChannel, NotificationChannelAdapter> channels;

    public NotificationPlatformService(
        NotificationScheduleRepository schedules,
        NotificationPreferenceRepository preferences,
        NotificationDeliveryAttemptRepository attempts,
        NotificationOperationRepository operations,
        ProfileService profiles,
        CanonicalTimeService time,
        CanonicalSafetyService safety,
        DurableJobQueue jobs,
        ObjectProvider<NotificationContentResolver> contentResolvers,
        ObjectProvider<NotificationConsentGuard> consentGuards,
        ObjectProvider<NotificationChannelAdapter> channels
    ) {
        this.schedules = schedules;
        this.preferences = preferences;
        this.attempts = attempts;
        this.operations = operations;
        this.profiles = profiles;
        this.time = time;
        this.safety = safety;
        this.jobs = jobs;
        this.contentResolvers = contentResolvers;
        this.consentGuards = consentGuards;
        this.channels = channels.orderedStream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                NotificationChannelAdapter::channel,
                adapter -> adapter
            )
        );
    }

    @Transactional
    public ScheduleWriteResult createSchedule(CreateSchedule command) {
        requireIdempotency(command.idempotencyKey());
        var previous = operations.findById(command.idempotencyKey()).orElse(null);
        if (previous != null) {
            if (!"CREATE_SCHEDULE".equals(previous.getOperation())) {
                throw new NotificationException("DUPLICATE", "Idempotency key reused for another notification operation");
            }
            var id = UUID.fromString(previous.getEntityRef());
            return new ScheduleWriteResult(
                schedules.findById(id).orElseThrow(() -> new NotificationException(
                    "NOTIFICATION_NOT_FOUND", "Notification schedule not found"
                )),
                true
            );
        }

        validateChannel(command.channel());
        validateTimezoneShape(command.timezoneMode(), command.pinnedTimezone());
        var next = resolveNext(
            command.userId(),
            command.startLocalDate(),
            command.localTime(),
            command.timezoneMode(),
            command.pinnedTimezone(),
            command.recurrence(),
            command.ambiguousPolicy(),
            command.nonexistentPolicy(),
            Instant.now()
        );
        if (command.recurrence() == NotificationRecurrence.ONCE && next == null) {
            throw new NotificationException("INVALID_SCHEDULE", "One-time notification is already in the past");
        }

        var schedule = new NotificationScheduleEntity(
            UUID.randomUUID(),
            command.userId(),
            command.contentKey(),
            command.pinnedContentVersion(),
            command.channel(),
            command.category(),
            command.purpose(),
            command.priority(),
            String.join(",", command.safetySignalKeys()),
            command.startLocalDate(),
            command.localTime(),
            command.timezoneMode(),
            command.pinnedTimezone(),
            command.recurrence(),
            command.ambiguousPolicy().name(),
            command.nonexistentPolicy().name(),
            command.retryMaxAttempts(),
            command.retryInitialBackoff().toSeconds(),
            command.retryPolicyVersion(),
            next
        );

        try {
            schedule = schedules.saveAndFlush(schedule);
            operations.saveAndFlush(new NotificationOperationEntity(
                command.idempotencyKey(),
                "CREATE_SCHEDULE",
                schedule.getScheduleId().toString()
            ));
        } catch (DataIntegrityViolationException conflict) {
            var replay = operations.findById(command.idempotencyKey())
                .orElseThrow(() -> conflict);
            return new ScheduleWriteResult(
                schedules.findById(UUID.fromString(replay.getEntityRef()))
                    .orElseThrow(() -> conflict),
                true
            );
        }
        return new ScheduleWriteResult(schedule, false);
    }

    @Transactional
    public PreferenceWriteResult upsertPreference(PreferenceCommand command) {
        requireIdempotency(command.idempotencyKey());

        var previous = operations.findById(command.idempotencyKey()).orElse(null);
        if (previous != null) {
            if (!"UPSERT_PREFERENCE".equals(previous.getOperation())) {
                throw new NotificationException("DUPLICATE", "Idempotency key reused for another notification operation");
            }
            var current = preferences.findByUserIdAndChannelAndCategoryAndPurpose(
                command.userId(), command.channel(), command.category(), command.purpose()
            ).orElseThrow();
            return new PreferenceWriteResult(current, true);
        }

        var current = preferences.findByUserIdAndChannelAndCategoryAndPurpose(
            command.userId(), command.channel(), command.category(), command.purpose()
        ).orElse(null);

        if (current == null) {
            current = new NotificationPreferenceEntity(
                command.userId(),
                command.channel(),
                command.category(),
                command.purpose(),
                command.enabled(),
                command.quietStart(),
                command.quietEnd(),
                command.quietPolicyVersion()
            );
        } else {
            if (command.expectedVersion() != null && current.getVersion() != command.expectedVersion()) {
                throw new NotificationException("VERSION_CONFLICT", "Notification preference version conflict");
            }
            current.update(
                command.enabled(),
                command.quietStart(),
                command.quietEnd(),
                command.quietPolicyVersion()
            );
        }

        current = preferences.saveAndFlush(current);
        operations.saveAndFlush(new NotificationOperationEntity(
            command.idempotencyKey(),
            "UPSERT_PREFERENCE",
            command.userId() + "|" + command.channel() + "|" + command.category() + "|" + command.purpose()
        ));
        return new PreferenceWriteResult(current, false);
    }

    @Transactional
    public List<EnqueueDecision> enqueueDue(Instant dueAt, int limit, String traceId) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }
        var due = schedules.findTop500ByStateAndNextDeliveryAtLessThanEqualOrderByNextDeliveryAtAsc(
            NotificationScheduleState.ACTIVE,
            dueAt
        ).stream().limit(limit).toList();

        var decisions = new ArrayList<EnqueueDecision>();
        for (var schedule : due) {
            decisions.add(enqueueOne(schedule, traceId));
        }
        return List.copyOf(decisions);
    }

    private EnqueueDecision enqueueOne(NotificationScheduleEntity schedule, String traceId) {
        var preference = preferences.findByUserIdAndChannelAndCategoryAndPurpose(
            schedule.getUserId(),
            schedule.getChannel(),
            schedule.getCategory(),
            schedule.getPurpose()
        ).orElse(null);

        if (preference != null && !preference.isEnabled()) {
            return new EnqueueDecision(schedule.getScheduleId(), null, "PREFERENCE_DISABLED", false);
        }

        var candidate = schedule.getNextDeliveryAt();
        var deferred = applyQuietHours(schedule, preference, candidate);
        if (deferred != null && deferred.isAfter(candidate)) {
            schedule.setNextDeliveryAt(deferred);
            schedules.save(schedule);
            return new EnqueueDecision(schedule.getScheduleId(), null, "QUIET_HOURS_DEFERRED", false);
        }

        var consentGuard = consentGuards.getIfAvailable();
        if (consentGuard == null || !consentGuard.allowed(
            schedule.getUserId(),
            schedule.getChannel(),
            schedule.getCategory(),
            schedule.getPurpose()
        )) {
            return new EnqueueDecision(schedule.getScheduleId(), null, "CONSENT_REQUIRED", false);
        }

        var resolver = contentResolvers.getIfAvailable();
        if (resolver == null) {
            return new EnqueueDecision(schedule.getScheduleId(), null, "CONTENT_UNAVAILABLE", false);
        }

        var profile = profiles.get(schedule.getUserId());
        if (profile == null) {
            return new EnqueueDecision(schedule.getScheduleId(), null, "CONTENT_UNAVAILABLE", false);
        }
        var zone = effectiveTimezone(schedule, profile.getTimezone());

        var evidence = resolver.resolve(new NotificationContentResolver.Request(
            schedule.getContentKey(),
            schedule.getPinnedContentVersion(),
            profile.getLocale(),
            profile.getCountryRegion(),
            zone.getId(),
            candidate,
            schedule.getPurpose(),
            schedule.getUserId(),
            traceId
        ));

        var attemptId = UUID.randomUUID();
        var now = Instant.now();
        var attempt = new NotificationDeliveryAttemptEntity(
            attemptId,
            schedule.getScheduleId(),
            candidate,
            1,
            DeliveryAttemptStatus.CREATED,
            evidence.contentId(),
            evidence.contentKey(),
            evidence.contentVersion(),
            evidence.schemaVersion(),
            evidence.resolvedLocale(),
            evidence.localePolicyVersion(),
            evidence.countryPolicyVersion(),
            schedule.getRetryPolicyVersion(),
            now,
            now
        );
        attempt = attempts.saveAndFlush(attempt);

        var key = "notification:" + schedule.getScheduleId() + ":" + candidate.getEpochSecond() + ":1";
        jobs.enqueue(
            JobType.NOTIFICATION,
            "notification-attempt:" + attemptId,
            key,
            traceId,
            schedule.getRetryMaxAttempts(),
            Duration.ofSeconds(schedule.getRetryInitialBackoffSeconds()),
            1
        );
        attempt.markEnqueued(Instant.now());
        attempts.save(attempt);

        if (schedule.getRecurrence() == NotificationRecurrence.DAILY) {
            var next = resolveNext(
                schedule.getUserId(),
                schedule.getStartLocalDate(),
                schedule.getLocalTime(),
                schedule.getTimezoneMode(),
                schedule.getPinnedTimezone(),
                schedule.getRecurrence(),
                AmbiguousLocalTimePolicy.valueOf(schedule.getAmbiguousPolicy()),
                NonexistentLocalTimePolicy.valueOf(schedule.getNonexistentPolicy()),
                candidate.plusSeconds(1)
            );
            schedule.setNextDeliveryAt(next);
        } else {
            schedule.setNextDeliveryAt(null);
        }
        schedules.save(schedule);

        return new EnqueueDecision(schedule.getScheduleId(), attemptId, "ENQUEUED", true);
    }

    private Instant applyQuietHours(
        NotificationScheduleEntity schedule,
        NotificationPreferenceEntity preference,
        Instant candidate
    ) {
        if (preference == null || preference.getQuietStart() == null || preference.getQuietEnd() == null) {
            return candidate;
        }

        var profile = profiles.get(schedule.getUserId());
        if (profile == null || profile.getTimezone() == null) return candidate;
        var zone = effectiveTimezone(schedule, profile.getTimezone());
        var local = candidate.atZone(zone).toLocalDateTime();
        var time = local.toLocalTime();
        var start = preference.getQuietStart();
        var end = preference.getQuietEnd();

        var inside = start.isBefore(end)
            ? !time.isBefore(start) && time.isBefore(end)
            : !time.isBefore(start) || time.isBefore(end);
        if (!inside) return candidate;

        if (schedule.getPriority() == NotificationPriority.CRITICAL) {
            var signals = Arrays.stream(schedule.getSafetySignalKeys().split(","))
                .filter(x -> !x.isBlank())
                .collect(java.util.stream.Collectors.toSet());
            var decision = safety.evaluate(signals);
            if (decision.severity().name().equals("CRITICAL") && !decision.blocked()) {
                return candidate;
            }
        }

        var date = start.isBefore(end)
            ? local.toLocalDate()
            : !time.isBefore(start) ? local.toLocalDate().plusDays(1) : local.toLocalDate();
        return time.convertScheduledLocalTime(
            LocalDateTime.of(date, end),
            zone,
            AmbiguousLocalTimePolicy.EARLIER_OFFSET,
            NonexistentLocalTimePolicy.SHIFT_FORWARD
        ).instant();
    }

    private Instant resolveNext(
        String userId,
        LocalDate startLocalDate,
        LocalTime localTime,
        NotificationTimezoneMode timezoneMode,
        String pinnedTimezone,
        NotificationRecurrence recurrence,
        AmbiguousLocalTimePolicy ambiguousPolicy,
        NonexistentLocalTimePolicy nonexistentPolicy,
        Instant referenceAt
    ) {
        var profile = profiles.get(userId);
        if (profile == null || profile.getTimezone() == null || profile.getTimezone().isBlank()) {
            throw new NotificationException("INVALID_TIMEZONE", "Profile timezone is unavailable");
        }
        var zone = timezoneMode == NotificationTimezoneMode.PINNED
            ? effectiveTimezone(null, pinnedTimezone)
            : effectiveTimezone(null, profile.getTimezone());

        var localReference = referenceAt.atZone(zone).toLocalDateTime();
        if (recurrence == NotificationRecurrence.ONCE) {
            var local = LocalDateTime.of(startLocalDate, localTime);
            var instant = time.convertScheduledLocalTime(
                local, zone, ambiguousPolicy, nonexistentPolicy
            ).instant();
            return instant.isBefore(referenceAt) ? null : instant;
        }

        var date = localReference.toLocalDate().isAfter(startLocalDate)
            ? localReference.toLocalDate() : startLocalDate;
        var local = LocalDateTime.of(date, localTime);
        var instant = time.convertScheduledLocalTime(
            local, zone, ambiguousPolicy, nonexistentPolicy
        ).instant();
        if (!instant.isAfter(referenceAt)) {
            local = LocalDateTime.of(date.plusDays(1), localTime);
            instant = time.convertScheduledLocalTime(
                local, zone, ambiguousPolicy, nonexistentPolicy
            ).instant();
        }
        return instant;
    }

    private ZoneId effectiveTimezone(NotificationScheduleEntity schedule, String id) {
        if (id == null || id.isBlank()) {
            throw new NotificationException("INVALID_TIMEZONE", "Notification timezone is required");
        }
        try {
            var zone = ZoneId.of(id);
            if (!ZoneId.getAvailableZoneIds().contains(zone.getId())) {
                throw new NotificationException("INVALID_TIMEZONE", "Notification timezone must be IANA");
            }
            return zone;
        } catch (NotificationException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new NotificationException("INVALID_TIMEZONE", "Invalid notification timezone");
        }
    }

    private void validateChannel(NotificationChannel channel) {
        var adapter = channels.get(channel);
        if (adapter != null && !adapter.available()) {
            throw new NotificationException("CHANNEL_UNAVAILABLE", "Notification channel unavailable");
        }
    }

    private void validateTimezoneShape(NotificationTimezoneMode mode, String pinned) {
        if ((mode == NotificationTimezoneMode.PINNED) != (pinned != null && !pinned.isBlank())) {
            throw new NotificationException("INVALID_TIMEZONE", "Pinned timezone mode mismatch");
        }
        if (mode == NotificationTimezoneMode.PINNED) effectiveTimezone(null, pinned);
    }

    private void requireIdempotency(String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new IllegalArgumentException("Valid notification idempotency key is required");
        }
    }

    public record CreateSchedule(
        String userId,
        String contentKey,
        Integer pinnedContentVersion,
        NotificationChannel channel,
        String category,
        String purpose,
        NotificationPriority priority,
        Set<String> safetySignalKeys,
        LocalDate startLocalDate,
        LocalTime localTime,
        NotificationTimezoneMode timezoneMode,
        String pinnedTimezone,
        NotificationRecurrence recurrence,
        AmbiguousLocalTimePolicy ambiguousPolicy,
        NonexistentLocalTimePolicy nonexistentPolicy,
        int retryMaxAttempts,
        Duration retryInitialBackoff,
        String retryPolicyVersion,
        String idempotencyKey,
        String traceId
    ) {}

    public record PreferenceCommand(
        String userId,
        NotificationChannel channel,
        String category,
        String purpose,
        boolean enabled,
        LocalTime quietStart,
        LocalTime quietEnd,
        String quietPolicyVersion,
        Long expectedVersion,
        String idempotencyKey
    ) {}

    public record ScheduleWriteResult(
        NotificationScheduleEntity schedule,
        boolean idempotentReplay
    ) {}

    public record PreferenceWriteResult(
        NotificationPreferenceEntity preference,
        boolean idempotentReplay
    ) {}

    public record EnqueueDecision(
        UUID scheduleId,
        UUID attemptId,
        String code,
        boolean enqueued
    ) {}

    public static class NotificationException extends RuntimeException {
        private final String code;
        public NotificationException(String code, String message) {
            super(message);
            this.code = code;
        }
        public String code() { return code; }
    }
}
