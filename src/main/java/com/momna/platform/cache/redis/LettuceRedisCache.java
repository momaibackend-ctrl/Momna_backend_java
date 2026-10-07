package com.momna.platform.cache.redis;

import com.momna.platform.cache.*;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;

public class LettuceRedisCache implements ExpiringCache, AutoCloseable {
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final String prefix;

    public LettuceRedisCache(RedisClient client, String prefix) {
        this.client = client;
        this.connection = client.connect();
        this.commands = connection.sync();
        this.prefix = prefix == null || prefix.isBlank() ? "momna" : prefix;
    }

    @Override
    public String get(CacheKey key) {
        try {
            return commands.get(key.redisKey(prefix));
        } catch (RedisException failure) {
            throw new CacheException("PROVIDER_UNAVAILABLE", "Redis read failed", failure);
        }
    }

    @Override
    public void put(CacheKey key, String value, Duration ttl) {
        requireTtl(ttl);
        try {
            commands.set(key.redisKey(prefix), value, SetArgs.Builder.px(ttl.toMillis()));
        } catch (RedisException failure) {
            throw new CacheException("PROVIDER_UNAVAILABLE", "Redis write failed", failure);
        }
    }

    @Override
    public boolean invalidate(CacheKey key) {
        try {
            return commands.del(key.redisKey(prefix)) > 0;
        } catch (RedisException failure) {
            throw new CacheException("PROVIDER_UNAVAILABLE", "Redis delete failed", failure);
        }
    }

    @Override
    public long invalidateNamespace(String namespace, String version) {
        var pattern = prefix + ":" + namespace + ":" + version + ":*";
        var args = ScanArgs.Builder.matches(pattern).limit(200);
        var cursor = ScanCursor.INITIAL;
        long deleted = 0;

        do {
            var result = commands.scan(cursor, args);
            if (!result.getKeys().isEmpty()) {
                deleted += commands.del(result.getKeys().toArray(String[]::new));
            }
            cursor = result;
        } while (!cursor.isFinished());

        return deleted;
    }

    @Override
    public boolean tryLock(CacheKey key, String ownerToken, Duration ttl) {
        requireToken(ownerToken);
        requireTtl(ttl);
        return "OK".equals(commands.set(
            key.redisKey(prefix),
            ownerToken,
            SetArgs.Builder.nx().px(ttl.toMillis())
        ));
    }

    @Override
    public boolean renewLock(CacheKey key, String ownerToken, Duration ttl) {
        requireToken(ownerToken);
        requireTtl(ttl);
        var script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end";
        var result = commands.eval(
            script,
            ScriptOutputType.INTEGER,
            new String[]{key.redisKey(prefix)},
            ownerToken,
            Long.toString(ttl.toMillis())
        );
        return result != null && result.longValue() == 1L;
    }

    @Override
    public boolean unlock(CacheKey key, String ownerToken) {
        requireToken(ownerToken);
        var script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        var result = commands.eval(
            script,
            ScriptOutputType.INTEGER,
            new String[]{key.redisKey(prefix)},
            ownerToken
        );
        return result != null && result.longValue() == 1L;
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
    }

    private void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cache TTL must be positive");
        }
    }

    private void requireToken(String ownerToken) {
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("Lock token is required");
        }
    }
}
