package io.github.rkunameneni.eventflow.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rkunameneni.eventflow.events.InventoryRejected;
import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.events.PaymentCompleted;
import io.github.rkunameneni.eventflow.events.PaymentFailed;
import io.github.rkunameneni.eventflow.order.domain.Order;
import io.github.rkunameneni.eventflow.order.domain.OrderRepository;
import io.github.rkunameneni.eventflow.order.domain.OrderStatus;
import io.github.rkunameneni.eventflow.support.messaging.OutboxWriter;
import io.github.rkunameneni.eventflow.support.messaging.ProcessedEventStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSagaHandlerTest {

    private static final Instant NOW = Instant.parse("2024-06-01T10:00:00Z");

    @Mock
    private OrderRepository orders;
    @Mock
    private ProcessedEventStore processedEvents;
    @Mock
    private OutboxWriter outbox;

    private OrderSagaHandler handler;
    private Order order;

    @BeforeEach
    void setUp() {
        handler = new OrderSagaHandler(orders, processedEvents, outbox, Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry());
        order = Order.place("alice", "USD", List.of(new Order.LineItem("SKU-1", 1, BigDecimal.TEN)), NOW);
    }

    @Test
    void inventoryReservationAdvancesTheOrderWithoutPublishing() {
        givenFirstDelivery();

        handler.on(new InventoryReserved(UUID.randomUUID(), order.getId(), "alice", BigDecimal.TEN, "USD", NOW));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
        verifyNoInteractions(outbox);
    }

    @Test
    void completedPaymentConfirmsTheOrder() {
        givenFirstDelivery();

        handler.on(new PaymentCompleted(UUID.randomUUID(), order.getId(), "alice", UUID.randomUUID(),
                BigDecimal.TEN, "USD", NOW));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        ArgumentCaptor<OrderConfirmed> event = ArgumentCaptor.forClass(OrderConfirmed.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(order.getId());
    }

    @Test
    void rejectedInventoryCancelsTheOrderWithTheReason() {
        givenFirstDelivery();

        handler.on(new InventoryRejected(UUID.randomUUID(), order.getId(), "alice", "INSUFFICIENT_STOCK: SKU-1", NOW));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<OrderCancelled> event = ArgumentCaptor.forClass(OrderCancelled.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().reason()).isEqualTo("INSUFFICIENT_STOCK: SKU-1");
    }

    @Test
    void failedPaymentAfterConfirmationIsIgnored() {
        givenFirstDelivery();
        order.confirm(NOW);

        handler.on(new PaymentFailed(UUID.randomUUID(), order.getId(), "alice", "DECLINED", NOW));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outbox, never()).append(any());
    }

    @Test
    void duplicateEventsAreSkipped() {
        when(processedEvents.markProcessed(any(), any())).thenReturn(false);

        handler.on(new PaymentFailed(UUID.randomUUID(), order.getId(), "alice", "DECLINED", NOW));

        verifyNoInteractions(orders, outbox);
    }

    @Test
    void eventsForUnknownOrdersFailSoTheyAreRetried() {
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);
        when(orders.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.on(
                new PaymentFailed(UUID.randomUUID(), UUID.randomUUID(), "alice", "DECLINED", NOW)))
                .isInstanceOf(IllegalStateException.class);
    }

    private void givenFirstDelivery() {
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
    }
}
