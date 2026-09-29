package io.github.rkunameneni.eventflow.inventory.application;

import java.util.Collection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Cache names and programmatic eviction for stock changes made by the saga, which touch several
 * SKUs at once and therefore cannot use a single {@code @CacheEvict} key.
 *
 * <p>The cache manager is transaction-aware, so evictions issued inside a transaction are applied
 * only after it commits - a concurrent reader cannot re-cache the pre-commit value.
 */
@Component
public class CatalogCache {

    public static final String PRODUCTS = "products";
    public static final String AVAILABILITY = "availability";

    private final CacheManager cacheManager;

    public CatalogCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictAvailability(Collection<String> skus) {
        Cache cache = cacheManager.getCache(AVAILABILITY);
        if (cache != null) {
            skus.forEach(cache::evict);
        }
    }
}
