package io.github.rkunameneni.eventflow.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Instant NOW = Instant.parse("2024-06-01T10:00:00Z");

    @Test
    void reservingMovesUnitsFromAvailableToReserved() {
        Product product = product(10);

        product.reserve(3, NOW);

        assertThat(product.getAvailableQuantity()).isEqualTo(7);
        assertThat(product.getReservedQuantity()).isEqualTo(3);
    }

    @Test
    void releasingPutsReservedUnitsBackOnSale() {
        Product product = product(10);
        product.reserve(3, NOW);

        product.release(3, NOW);

        assertThat(product.getAvailableQuantity()).isEqualTo(10);
        assertThat(product.getReservedQuantity()).isZero();
    }

    @Test
    void committingRemovesReservedUnits() {
        Product product = product(10);
        product.reserve(3, NOW);

        product.commit(3, NOW);

        assertThat(product.getAvailableQuantity()).isEqualTo(7);
        assertThat(product.getReservedQuantity()).isZero();
    }

    @Test
    void stockCannotGoNegative() {
        Product product = product(2);

        assertThat(product.canReserve(3)).isFalse();
        assertThatThrownBy(() -> product.reserve(3, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> product.release(1, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pricesAreComparedByValueAndCurrency() {
        Product product = product(1);

        assertThat(product.isPricedAt(new BigDecimal("19.990"), "USD")).isTrue();
        assertThat(product.isPricedAt(new BigDecimal("19.99"), "EUR")).isFalse();
        assertThat(product.isPricedAt(new BigDecimal("18.99"), "USD")).isFalse();
    }

    private static Product product(int available) {
        return new Product("SKU-1", "Widget", new BigDecimal("19.99"), "USD", available, NOW);
    }
}
