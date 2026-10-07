package com.momna.platform.localization;

import java.time.*;
import org.springframework.stereotype.Service;

@Service
public class CanonicalTimeService {
    public ZonedDateTime getLocalNow(ZoneId timezone, Clock clock) {
        return ZonedDateTime.ofInstant(clock.instant(), requireIana(timezone));
    }

    public LocalDate getLocalDate(ZoneId timezone, Instant instant) {
        return instant.atZone(requireIana(timezone)).toLocalDate();
    }

    public DayWindow resolveDayWindow(LocalDate localDate, ZoneId timezone) {
        var zone = requireIana(timezone);
        var start = localDate.atStartOfDay(zone).toInstant();
        var end = localDate.plusDays(1).atStartOfDay(zone).toInstant();
        return new DayWindow(localDate, zone, start, end);
    }

    public ScheduledLocalTimeResult convertScheduledLocalTime(
        LocalDateTime localDateTime,
        ZoneId timezone,
        AmbiguousLocalTimePolicy ambiguousPolicy,
        NonexistentLocalTimePolicy nonexistentPolicy
    ) {
        var zone = requireIana(timezone);
        var offsets = zone.getRules().getValidOffsets(localDateTime);

        if (offsets.size() == 1) {
            return new ScheduledLocalTimeResult(
                localDateTime,
                localDateTime,
                zone,
                localDateTime.toInstant(offsets.getFirst()),
                false,
                false
            );
        }

        if (offsets.size() == 2) {
            var offset = switch (ambiguousPolicy) {
                case REJECT -> throw new LocalizationException(
                    "AMBIGUOUS_LOCAL_TIME", "Ambiguous local time"
                );
                case EARLIER_OFFSET -> offsets.getFirst();
                case LATER_OFFSET -> offsets.getLast();
            };
            return new ScheduledLocalTimeResult(
                localDateTime,
                localDateTime,
                zone,
                localDateTime.toInstant(offset),
                false,
                true
            );
        }

        if (offsets.isEmpty()) {
            if (nonexistentPolicy == NonexistentLocalTimePolicy.REJECT) {
                throw new LocalizationException(
                    "NONEXISTENT_LOCAL_TIME", "Nonexistent local time"
                );
            }
            var transition = zone.getRules().getTransition(localDateTime);
            if (transition == null) {
                throw new LocalizationException(
                    "NONEXISTENT_LOCAL_TIME", "Nonexistent local time"
                );
            }
            var adjusted = transition.getDateTimeAfter();
            return new ScheduledLocalTimeResult(
                localDateTime,
                adjusted,
                zone,
                adjusted.toInstant(transition.getOffsetAfter()),
                true,
                false
            );
        }

        throw new LocalizationException("VALIDATION_ERROR", "Unexpected timezone offsets");
    }

    public EventLocalTime captureEventLocalTime(Instant occurredAt, ZoneId timezone) {
        var zone = requireIana(timezone);
        return new EventLocalTime(
            occurredAt,
            zone.getId(),
            occurredAt.atZone(zone).toLocalDate()
        );
    }

    private ZoneId requireIana(ZoneId zone) {
        if (zone == null || !ZoneId.getAvailableZoneIds().contains(zone.getId())) {
            throw new LocalizationException("INVALID_TIMEZONE", "Timezone must be an IANA zone ID");
        }
        return zone;
    }

    public record DayWindow(
        LocalDate localDate,
        ZoneId timezone,
        Instant startInclusive,
        Instant endExclusive
    ) {}

    public record ScheduledLocalTimeResult(
        LocalDateTime requestedLocalDateTime,
        LocalDateTime resolvedLocalDateTime,
        ZoneId timezone,
        Instant instant,
        boolean adjustedForGap,
        boolean ambiguousOffsetResolved
    ) {}

    public record EventLocalTime(
        Instant occurredAt,
        String timezoneAtEvent,
        LocalDate localDateAtEvent
    ) {}
}
