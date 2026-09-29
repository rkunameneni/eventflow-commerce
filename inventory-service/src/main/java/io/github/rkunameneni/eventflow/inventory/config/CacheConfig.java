package io.github.rkunameneni.eventflow.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkunameneni.eventflow.inventory.application.CatalogCache;
import io.github.rkunameneni.eventflow.inventory.application.ProductView;
import io.github.rkunameneni.eventflow.inventory.application.StockLevel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Redis cache setup: one TTL per cache and a typed JSON serializer per cache (readable in
 * {@code redis-cli}, no Java serialization, no polymorphic type metadata to exploit).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer eventflowCaches(
            ObjectMapper objectMapper,
            @Value("${eventflow.cache.products-ttl:10m}") Duration productsTtl,
            @Value("${eventflow.cache.availability-ttl:30s}") Duration availabilityTtl) {
        return builder -> builder
                .transactionAware()
                .enableStatistics()
                .withCacheConfiguration(CatalogCache.PRODUCTS,
                        cacheConfiguration(productsTtl, new Jackson2JsonRedisSerializer<>(objectMapper, ProductView.class)))
                .withCacheConfiguration(CatalogCache.AVAILABILITY,
                        cacheConfiguration(availabilityTtl, new Jackson2JsonRedisSerializer<>(objectMapper, StockLevel.class)));
    }

    private static RedisCacheConfiguration cacheConfiguration(Duration ttl, Jackson2JsonRedisSerializer<?> serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("inventory::")
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }
}
