package io.github.rkunameneni.eventflow.inventory.application;

import io.github.rkunameneni.eventflow.inventory.domain.Product;
import io.github.rkunameneni.eventflow.inventory.domain.ProductRepository;
import io.github.rkunameneni.eventflow.support.web.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-mostly catalogue with cache-aside lookups: {@code @Cacheable} reads Redis first and only
 * queries Postgres on a miss; writes evict the affected keys. Product details and stock levels
 * are cached separately because they change at very different rates (see the TTLs in
 * {@code CacheConfig}).
 */
@Service
public class CatalogService {

    private final ProductRepository products;
    private final Clock clock;

    public CatalogService(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    @Cacheable(cacheNames = CatalogCache.PRODUCTS, key = "#sku")
    @Transactional(readOnly = true)
    public ProductView product(String sku) {
        return ProductView.from(find(sku));
    }

    @Cacheable(cacheNames = CatalogCache.AVAILABILITY, key = "#sku")
    @Transactional(readOnly = true)
    public StockLevel availability(String sku) {
        return StockLevel.from(find(sku));
    }

    @Transactional(readOnly = true)
    public Page<ProductView> products(Pageable pageable) {
        return products.findAll(pageable).map(ProductView::from);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = CatalogCache.PRODUCTS, key = "#sku"),
        @CacheEvict(cacheNames = CatalogCache.AVAILABILITY, key = "#sku")
    })
    @Transactional
    public ProductView upsert(String sku, String name, BigDecimal unitPrice, String currency, int availableQuantity) {
        Product product = products.findById(sku)
                .map(existing -> {
                    existing.update(name, unitPrice, currency, availableQuantity, clock.instant());
                    return existing;
                })
                .orElseGet(() -> products.save(
                        new Product(sku, name, unitPrice, currency, availableQuantity, clock.instant())));
        return ProductView.from(product);
    }

    private Product find(String sku) {
        return products.findById(sku).orElseThrow(() -> new ResourceNotFoundException("Product", sku));
    }
}
