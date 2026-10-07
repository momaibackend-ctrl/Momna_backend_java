package com.momna.platform.cache.redis;

import com.momna.platform.cache.ExpiringCache;
import io.lettuce.core.RedisClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name = "momna.redis.enabled", havingValue = "true")
public class RedisCacheConfiguration {
    @Bean
    RedisClient redisClient(@Value("${momna.redis.url}") String redisUrl) {
        return RedisClient.create(redisUrl);
    }

    @Bean(destroyMethod = "close")
    ExpiringCache expiringCache(
        RedisClient redisClient,
        @Value("${momna.redis.prefix:momna}") String prefix
    ) {
        return new LettuceRedisCache(redisClient, prefix);
    }
}
