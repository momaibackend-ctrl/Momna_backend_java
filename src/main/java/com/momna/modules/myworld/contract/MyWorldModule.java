package com.momna.modules.myworld.contract;

import com.momna.modules.integration.IntegrationConsumer;

public interface MyWorldModule {
    default IntegrationConsumer integrationConsumer() {
        return IntegrationConsumer.MYWORLD;
    }
}
