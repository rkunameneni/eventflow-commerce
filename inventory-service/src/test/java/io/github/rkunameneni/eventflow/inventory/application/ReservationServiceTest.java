package io.github.rkunameneni.eventflow.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rkunameneni.eventflow.events.InventoryRejected;
import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderCreated;
import io.github.rkunameneni.eventflow.events.OrderLine;
import io.github.rkunameneni.eventflow.inventory.domain.Product;
import io.github.rkunameneni.eventflow.inventory.domain.ProductRepository;
import io.github.rkunameneni.eventflow.inventory.domain.Reservation;
import io.github.rkunameneni.eventflow.inventory.domain.ReservationRepository;
import io.github.rkunameneni.eventflow.support.messaging.OutboxWriter;
import io.github.rkunameneni.eventflow.support.messaging.ProcessedEventStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2024-06-01T10:00:00Z");

    @Mock
    private ProductRepository products;
    @Mock
    private ReservationRepository reservations;
    @Mock
    private ProcessedEventStore processedEvents;
    @Mock
    private OutboxWriter outbox;
    @Mock
    private CatalogCache catalogCache;

    private ReservationService service;
    private Product widget;

    @BeforeEach
    void setUp() {
        service = new ReservationService(products, reservations, processedEvents, outbox, catalogCache,
                Clock.fixed(NOW, ZoneOffset.UTC));
        widget = new Product("SKU-1", "Widget", new BigDecimal("10.00"), "USD", 5, NOW);
    }

    @Test
    void reservesEveryLineAndPublishesInventoryReserved() {
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);
        when(products.lockAllBySkuIn(anyCollection())).thenReturn(List.of(widget));

        service.reserve(orderFor(2));

        assertThat(widget.getAvailableQuantity()).isEqualTo(3);
        verify(reservations).save(any(Reservation.class));
        verify(catalogCache).evictAvailability(List.of("SKU-1"));
        ArgumentCaptor<InventoryReserved> event = ArgumentCaptor.forClass(InventoryReserved.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().totalAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void rejectsTheWholeOrderWithoutTouchingStock() {
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);
        when(products.lockAllBySkuIn(anyCollection())).thenReturn(List.of(widget));

        service.reserve(orderFor(6));

        assertThat(widget.getAvailableQuantity()).isEqualTo(5);
        verify(reservations, never()).save(any());
        ArgumentCaptor<InventoryRejected> event = ArgumentCaptor.forClass(InventoryRejected.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().reason()).isEqualTo("INSUFFICIENT_STOCK: SKU-1");
    }

    @Test
    void duplicateOrderCreatedEventsAreIgnored() {
        when(processedEvents.markProcessed(any(), any())).thenReturn(false);

        service.reserve(orderFor(1));

        verifyNoInteractions(products, reservations, outbox);
    }

    @Test
    void cancellationReleasesHeldReservations() {
        UUID orderId = UUID.randomUUID();
        widget.reserve(2, NOW);
        var reservation = new Reservation(orderId, "SKU-1", 2, NOW);
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);
        when(reservations.findByOrderIdAndStatus(orderId, Reservation.Status.RESERVED)).thenReturn(List.of(reservation));
        when(products.lockAllBySkuIn(anyCollection())).thenReturn(List.of(widget));

        service.release(new OrderCancelled(UUID.randomUUID(), orderId, "alice", "PAYMENT_DECLINED", NOW));

        assertThat(widget.getAvailableQuantity()).isEqualTo(5);
        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.RELEASED);
    }

    @Test
    void cancellationOfAnUnreservedOrderIsANoOp() {
        UUID orderId = UUID.randomUUID();
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);
        when(reservations.findByOrderIdAndStatus(orderId, Reservation.Status.RESERVED)).thenReturn(List.of());

        service.release(new OrderCancelled(UUID.randomUUID(), orderId, "alice", "INSUFFICIENT_STOCK", NOW));

        verifyNoInteractions(products, catalogCache);
    }

    private static OrderCreated orderFor(int quantity) {
        var line = new OrderLine("SKU-1", quantity, new BigDecimal("10.00"));
        return new OrderCreated(UUID.randomUUID(), UUID.randomUUID(), "alice", List.of(line), line.lineTotal(),
                "USD", NOW);
    }
}
