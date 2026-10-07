package com.momna.modules.myday.contract;

import com.momna.modules.integration.IntegrationConsumer;

public interface MyDayModule {
    default IntegrationConsumer integrationConsumer() {
        return IntegrationConsumer.MYDAY;
    }
}
