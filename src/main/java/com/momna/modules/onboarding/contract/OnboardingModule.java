package com.momna.modules.onboarding.contract;

import com.momna.modules.integration.IntegrationConsumer;

public interface OnboardingModule {
    default IntegrationConsumer integrationConsumer() {
        return IntegrationConsumer.ONBOARDING;
    }
}
