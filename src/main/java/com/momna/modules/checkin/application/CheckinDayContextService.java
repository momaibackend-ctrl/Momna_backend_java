package com.momna.modules.checkin.application;

import com.momna.modules.checkin.domain.*;
import com.momna.modules.lifecycle.application.LifecycleQueryService;
import com.momna.modules.profile.application.ProfileService;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CheckinDayContextService {
    private final ProfileService profiles;
    private final LifecycleQueryService lifecycle;
    private final LocalTime morningOpen;
    private final LocalTime morningClose;
    private final LocalTime eveningOpen;
    private final LocalTime eveningClose;

    public CheckinDayContextService(
        ProfileService profiles,
        LifecycleQueryService lifecycle,
        @Value("${momna.checkin.windows.morning-open:00:00}") String morningOpen,
        @Value("${momna.checkin.windows.morning-close:12:00}") String morningClose,
        @Value("${momna.checkin.windows.evening-open:12:00}") String eveningOpen,
        @Value("${momna.checkin.windows.evening-close:23:59:59}") String eveningClose
    ) {
        this.profiles = profiles;
        this.lifecycle = lifecycle;
        this.morningOpen = LocalTime.parse(morningOpen);
        this.morningClose = LocalTime.parse(morningClose);
        this.eveningOpen = LocalTime.parse(eveningOpen);
        this.eveningClose = LocalTime.parse(eveningClose);
    }

    public DayContext resolve(String userId, Instant at) {
        var profile = profiles.get(userId);
        if (profile == null || profile.getTimezone() == null || profile.getTimezone().isBlank()) {
            throw new CheckinException("DEPENDENCY_UNAVAILABLE", "Canonical user timezone is unavailable");
        }

        ZoneId timezone;
        try {
            timezone = ZoneId.of(profile.getTimezone());
        } catch (RuntimeException failure) {
            throw new CheckinException("DEPENDENCY_UNAVAILABLE", "Canonical user timezone is invalid");
        }

        var snapshot = lifecycle.current(userId, at);
        if (snapshot.primary() == null) {
            throw new CheckinException("CHECKIN_NOT_ELIGIBLE", "Lifecycle period is required for Check-in");
        }

        CheckinDefinitionPeriod period;
        try {
            period = CheckinDefinitionPeriod.valueOf(snapshot.primary().period().name());
        } catch (RuntimeException failure) {
            throw new CheckinException("CHECKIN_NOT_ELIGIBLE", "Lifecycle period is not eligible for Check-in");
        }

        var localDate = at.atZone(timezone).toLocalDate();
        return new DayContext(
            localDate,
            timezone,
            period,
            snapshot.primary().substage(),
            window(localDate, timezone, CheckinPhase.MORNING),
            window(localDate, timezone, CheckinPhase.EVENING)
        );
    }

    private Window window(LocalDate date, ZoneId timezone, CheckinPhase phase) {
        var open = phase == CheckinPhase.MORNING ? morningOpen : eveningOpen;
        var close = phase == CheckinPhase.MORNING ? morningClose : eveningClose;
        var opensAt = date.atTime(open).atZone(timezone).toInstant();
        var closesAt = date.atTime(close).atZone(timezone).toInstant();
        if (!closesAt.isAfter(opensAt)) {
            closesAt = date.plusDays(1).atTime(close).atZone(timezone).toInstant();
        }
        return new Window(opensAt, closesAt);
    }

    public record DayContext(
        LocalDate localDate,
        ZoneId timezone,
        CheckinDefinitionPeriod period,
        String substage,
        Window morning,
        Window evening
    ) {
        public Window window(CheckinPhase phase) {
            return phase == CheckinPhase.MORNING ? morning : evening;
        }
    }

    public record Window(Instant opensAt, Instant closesAt) {
        public String stateAt(Instant at) {
            if (at.isBefore(opensAt)) return "NOT_OPEN_YET";
            if (!at.isBefore(closesAt)) return "CLOSED";
            return "OPEN";
        }
    }
}
