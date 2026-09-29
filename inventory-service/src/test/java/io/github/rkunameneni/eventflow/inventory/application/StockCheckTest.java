package io.github.rkunameneni.eventflow.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkunameneni.eventflow.events.OrderCreated;
import io.github.rkunameneni.eventflow.events.OrderLine;
import io.github.rkunameneni.eventflow.inventory.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockCheckTest {

    private static final Instant NOW = Instant.parse("2024-06-01T10:00:00Z");
    private static final Map<String, Product> CATALOGUE = Map.of(
            "SKU-1", new Product("SKU-1", "Widget", new BigDecimal("10.00"), "USD", 5, NOW),
            "SKU-2", new Product("SKU-2", "Gadget", new BigDecimal("4.50"), "USD", 1, NOW));

    @Test
    void acceptsAnOrderThatCanBeFullyReserved() {
        var order = order("USD", new OrderLine("SKU-1", 5, new BigDecimal("10")), new OrderLine("SKU-2", 1,
                new BigDecimal("4.5")));

        assertThat(StockCheck.rejectionReason(order, CATALOGUE)).isEmpty();
    }

    @Test
    void rejectsUnknownSkus() {
        var order = order("USD", new OrderLine("SKU-404", 1, BigDecimal.ONE));

        assertThat(StockCheck.rejectionReason(order, CATALOGUE)).hasValue("UNKNOWN_SKU: SKU-404");
    }

    @Test
    void rejectsStaleQuotedPrices() {
        var order = order("USD", new OrderLine("SKU-1", 1, new BigDecimal("9.00")));

        assertThat(StockCheck.rejectionReason(order, CATALOGUE)).hasValue("PRICE_CHANGED: SKU-1 now costs 10.00 USD");
    }

    @Test
    void rejectsOrdersInAnotherCurrency() {
        var order = order("EUR", new OrderLine("SKU-1", 1, new BigDecimal("10.00")));

        assertThat(StockCheck.rejectionReason(order, CATALOGUE)).get().asString().startsWith("PRICE_CHANGED");
    }

    @Test
    void rejectsWhenAnyLineIsShort() {
        var order = order("USD", new OrderLine("SKU-1", 1, new BigDecimal("10.00")),
                new OrderLine("SKU-2", 2, new BigDecimal("4.50")));

        assertThat(StockCheck.rejectionReason(order, CATALOGUE)).hasValue("INSUFFICIENT_STOCK: SKU-2");
    }

    private static OrderCreated order(String currency, OrderLine... lines) {
        BigDecimal total = List.of(lines).stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderCreated(UUID.randomUUID(), UUID.randomUUID(), "alice", List.of(lines), total, currency, NOW);
    }
}
