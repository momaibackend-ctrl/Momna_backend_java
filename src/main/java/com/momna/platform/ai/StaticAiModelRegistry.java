package com.momna.platform.ai;

import com.momna.platform.ai.AiContracts.ModelRoutingPolicy;
import com.momna.platform.ai.AiPorts.ModelRegistry;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StaticAiModelRegistry implements ModelRegistry {
    private final Map<String, Map<Integer, ModelRoutingPolicy>> byKey;

    public StaticAiModelRegistry(Collection<ModelRoutingPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("AI model registry must not be empty");
        }
        if (policies.size() != policies.stream()
            .map(x -> x.key() + ":" + x.version()).distinct().count()) {
            throw new IllegalArgumentException("Duplicate AI model policy");
        }
        this.byKey = policies.stream().collect(Collectors.groupingBy(
            ModelRoutingPolicy::key,
            Collectors.toUnmodifiableMap(ModelRoutingPolicy::version, Function.identity())
        ));
    }

    @Override
    public ModelRoutingPolicy resolvePolicy(String key, Integer version) {
        var versions = byKey.get(key);
        if (versions == null) return null;
        if (version != null) return versions.get(version);
        return versions.values().stream()
            .max(Comparator.comparingInt(ModelRoutingPolicy::version))
            .orElse(null);
    }
}
