package com.momna.modules.calendar;

import com.momna.platform.localization.CanonicalTimeService;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class CalendarLocalTimeAdapter {
    private final CanonicalTimeService timeService;

    public CalendarLocalTimeAdapter(CanonicalTimeService timeService) {
        this.timeService = timeService;
    }

    public CanonicalTimeService.EventLocalTime stampEvent(
        Instant occurredAt,
        ZoneId currentTimezone
    ) {
        return timeService.captureEventLocalTime(occurredAt, currentTimezone);
    }
}
