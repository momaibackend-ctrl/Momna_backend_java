package com.momna.platform.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A process-local {@link ExpiringCache} for when Redis is disabled. Entries do not survive a restart
 * and are not shared between instances; callers that need durability keep their own store.
 */
public class InMemoryExpiringCache implements ExpiringCache {
    private static final int MAX_ENTRIES = 10_000;

    private record Entry(String value, Instant expiresAt) {}

    private final ConcurrentHashMap<CacheKey, Entry> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey, Entry> locks = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryExpiringCache() {
        this(Clock.systemUTC());
    }

    public InMemoryExpiringCache(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String get(CacheKey key) {
        var entry = live(values, key);
        return entry == null ? null : entry.value();
    }

    @Override
    public void put(CacheKey key, String value, Duration ttl) {
        if (values.size() >= MAX_ENTRIES) {
            var now = clock.instant();
            values.entrySet().removeIf(x -> !x.getValue().expiresAt().isAfter(now));
            if (values.size() >= MAX_ENTRIES) values.clear();
        }
        values.put(key, new Entry(value, clock.instant().plus(ttl)));
    }

    @Override
    public boolean invalidate(CacheKey key) {
        return values.remove(key) != null;
    }

    @Override
    public long invalidateNamespace(String namespace, String version) {
        var before = values.size();
        values.keySet().removeIf(x -> x.namespace().equals(namespace) && x.version().equals(version));
        return before - values.size();
    }

    @Override
    public boolean tryLock(CacheKey key, String ownerToken, Duration ttl) {
        var now = clock.instant();
        var fresh = new Entry(ownerToken, now.plus(ttl));
        var held = locks.compute(key, (k, current) ->
            current == null || !current.expiresAt().isAfter(now) ? fresh : current);
        return held == fresh;
    }

    @Override
    public boolean renewLock(CacheKey key, String ownerToken, Duration ttl) {
        var now = clock.instant();
        var renewed = locks.computeIfPresent(key, (k, current) ->
            current.value().equals(ownerToken) && current.expiresAt().isAfter(now)
                ? new Entry(ownerToken, now.plus(ttl)) : current);
        return renewed != null && renewed.value().equals(ownerToken) && renewed.expiresAt().isAfter(now);
    }

    @Override
    public boolean unlock(CacheKey key, String ownerToken) {
        var removed = new boolean[1];
        locks.computeIfPresent(key, (k, current) -> {
            if (current.value().equals(ownerToken)) {
                removed[0] = true;
                return null;
            }
            return current;
        });
        return removed[0];
    }

    private Entry live(ConcurrentHashMap<CacheKey, Entry> map, CacheKey key) {
        var entry = map.get(key);
        if (entry == null) return null;
        if (!entry.expiresAt().isAfter(clock.instant())) {
            map.remove(key, entry);
            return null;
        }
        return entry;
    }
}
