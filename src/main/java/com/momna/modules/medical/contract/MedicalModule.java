package com.momna.modules.medical.contract;

import com.momna.modules.integration.IntegrationConsumer;

public interface MedicalModule {
    default IntegrationConsumer integrationConsumer() {
        return IntegrationConsumer.MEDICAL;
    }
}
