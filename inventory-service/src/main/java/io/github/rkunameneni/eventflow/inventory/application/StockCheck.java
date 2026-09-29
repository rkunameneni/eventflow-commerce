package io.github.rkunameneni.eventflow.inventory.application;

import io.github.rkunameneni.eventflow.events.OrderCreated;
import io.github.rkunameneni.eventflow.events.OrderLine;
import io.github.rkunameneni.eventflow.inventory.domain.Product;
import java.util.Map;
import java.util.Optional;

/**
 * Decides whether an order can be reserved in full. Kept free of I/O so every rejection rule is
 * unit-testable; the reason strings are part of the {@code InventoryRejected} contract.
 */
public final class StockCheck {

    private StockCheck() {
    }

    public static Optional<String> rejectionReason(OrderCreated order, Map<String, Product> productsBySku) {
        for (OrderLine line : order.lines()) {
            Product product = productsBySku.get(line.sku());
            if (product == null) {
                return Optional.of("UNKNOWN_SKU: " + line.sku());
            }
            if (!product.isPricedAt(line.unitPrice(), order.currency())) {
                return Optional.of("PRICE_CHANGED: " + line.sku() + " now costs "
                        + product.getUnitPrice().toPlainString() + " " + product.getCurrency());
            }
            if (!product.canReserve(line.quantity())) {
                return Optional.of("INSUFFICIENT_STOCK: " + line.sku());
            }
        }
        return Optional.empty();
    }
}
