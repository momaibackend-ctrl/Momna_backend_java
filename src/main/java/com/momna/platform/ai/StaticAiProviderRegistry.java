package com.momna.platform.ai;

import com.momna.platform.ai.AiPorts.ProviderAdapter;
import com.momna.platform.ai.AiPorts.ProviderRegistry;
import java.util.*;
import java.util.stream.Collectors;

public class StaticAiProviderRegistry implements ProviderRegistry {
    private final Map<String, ProviderAdapter> byKey;

    public StaticAiProviderRegistry(Collection<ProviderAdapter> adapters) {
        if (adapters == null || adapters.isEmpty()) {
            throw new IllegalArgumentException("AI provider registry must not be empty");
        }
        this.byKey = adapters.stream().collect(Collectors.toUnmodifiableMap(
            ProviderAdapter::providerKey, adapter -> adapter
        ));
    }

    @Override
    public ProviderAdapter adapter(String providerKey) {
        return byKey.get(providerKey);
    }
}
