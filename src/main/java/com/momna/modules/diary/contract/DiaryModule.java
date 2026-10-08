package com.momna.modules.diary.contract;

import com.momna.modules.integration.IntegrationConsumer;

public interface DiaryModule {
    default IntegrationConsumer integrationConsumer() {
        return IntegrationConsumer.DIARY;
    }
}
