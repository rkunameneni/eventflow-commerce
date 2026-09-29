package io.github.rkunameneni.eventflow.inventory.application;

import io.github.rkunameneni.eventflow.inventory.domain.Product;

public record StockLevel(String sku, int available, int reserved) {

    static StockLevel from(Product product) {
        return new StockLevel(product.getSku(), product.getAvailableQuantity(), product.getReservedQuantity());
    }
}
