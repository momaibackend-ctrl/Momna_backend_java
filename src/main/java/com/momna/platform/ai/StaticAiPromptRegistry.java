package com.momna.platform.ai;

import com.momna.platform.ai.AiContracts.PromptDefinition;
import com.momna.platform.ai.AiPorts.PromptRegistry;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StaticAiPromptRegistry implements PromptRegistry {
    private final Map<String, Map<Integer, PromptDefinition>> byKey;

    public StaticAiPromptRegistry(Collection<PromptDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("AI prompt registry must not be empty");
        }
        if (definitions.size() != definitions.stream()
            .map(x -> x.key() + ":" + x.version()).distinct().count()) {
            throw new IllegalArgumentException("Duplicate AI prompt definition");
        }
        this.byKey = definitions.stream().collect(Collectors.groupingBy(
            PromptDefinition::key,
            Collectors.toUnmodifiableMap(PromptDefinition::version, Function.identity())
        ));
    }

    @Override
    public PromptDefinition resolve(String key, Integer version) {
        var versions = byKey.get(key);
        if (versions == null) return null;
        if (version != null) return versions.get(version);
        return versions.values().stream()
            .max(Comparator.comparingInt(PromptDefinition::version))
            .orElse(null);
    }
}
