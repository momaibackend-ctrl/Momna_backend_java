package com.momna.modules.agent.contract;

import com.momna.modules.integration.IntegrationConsumer;

public interface AgentModule {
    default IntegrationConsumer integrationConsumer() {
        return IntegrationConsumer.AGENT;
    }
}
