package com.momna.platform.cache;

public record CacheKey(String namespace, String version, String key) {
    public CacheKey {
        if (namespace == null || !namespace.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid cache namespace");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Invalid cache version");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Invalid cache key");
        }
    }

    public String redisKey(String prefix) {
        return prefix + ":" + namespace + ":" + version + ":" + key;
    }
}
