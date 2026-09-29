package io.github.rkunameneni.eventflow.inventory.application;

import io.github.rkunameneni.eventflow.inventory.domain.Product;
import java.math.BigDecimal;

public record ProductView(String sku, String name, BigDecimal unitPrice, String currency) {

    static ProductView from(Product product) {
        return new ProductView(product.getSku(), product.getName(), product.getUnitPrice(), product.getCurrency());
    }
}
