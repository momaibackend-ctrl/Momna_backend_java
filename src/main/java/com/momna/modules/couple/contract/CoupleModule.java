package com.momna.modules.couple.contract;

import com.momna.modules.integration.IntegrationConsumer;

public interface CoupleModule {
    default IntegrationConsumer integrationConsumer() {
        return IntegrationConsumer.COUPLE;
    }
}
