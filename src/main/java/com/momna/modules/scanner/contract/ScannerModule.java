package com.momna.modules.scanner.contract;

import com.momna.modules.integration.IntegrationConsumer;

public interface ScannerModule {
    default IntegrationConsumer integrationConsumer() {
        return IntegrationConsumer.SCANNER;
    }
}
