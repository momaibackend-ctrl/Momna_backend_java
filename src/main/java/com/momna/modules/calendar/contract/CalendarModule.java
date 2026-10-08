package com.momna.modules.calendar.contract;

import com.momna.modules.integration.IntegrationConsumer;

public interface CalendarModule {
    default IntegrationConsumer integrationConsumer() {
        return IntegrationConsumer.CALENDAR;
    }
}
