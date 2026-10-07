package com.momna.platform.cache;

import java.time.Duration;

public interface ExpiringCache {
    String get(CacheKey key);
    void put(CacheKey key, String value, Duration ttl);
    boolean invalidate(CacheKey key);
    long invalidateNamespace(String namespace, String version);
    boolean tryLock(CacheKey key, String ownerToken, Duration ttl);
    boolean renewLock(CacheKey key, String ownerToken, Duration ttl);
    boolean unlock(CacheKey key, String ownerToken);
}
