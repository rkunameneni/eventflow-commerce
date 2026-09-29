package io.github.rkunameneni.eventflow.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Instant T0 = Instant.parse("2024-06-01T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(5);

    @Test
    void placingAnOrderComputesTheTotalAndStartsPending() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("109.97");
        assertThat(order.getItems()).extracting(OrderItem::getLineNumber).containsExactly(1, 2);
    }

    @Test
    void anOrderNeedsAtLeastOneLine() {
        assertThatThrownBy(() -> Order.place("alice", "USD", List.of(), T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void happyPathMovesThroughReservationToConfirmation() {
        Order order = newOrder();

        assertThat(order.markInventoryReserved(T1)).isTrue();
        assertThat(order.confirm(T1)).isTrue();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void paymentMayBeSeenBeforeTheReservation() {
        Order order = newOrder();

        assertThat(order.confirm(T1)).isTrue();
        assertThat(order.markInventoryReserved(T1)).as("late reservation is ignored").isFalse();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void terminalStatesAreFinal() {
        Order order = newOrder();
        order.cancel("INSUFFICIENT_STOCK", T1);

        assertThat(order.confirm(T1)).isFalse();
        assertThat(order.cancel("again", T1)).isFalse();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatusReason()).isEqualTo("INSUFFICIENT_STOCK");
    }

    private static Order newOrder() {
        return Order.place("alice", "USD", List.of(
                new Order.LineItem("SKU-1", 2, new BigDecimal("19.99")),
                new Order.LineItem("SKU-2", 1, new BigDecimal("69.99"))), T0);
    }
}
