package io.github.rkunameneni.eventflow.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rkunameneni.eventflow.events.OrderCreated;
import io.github.rkunameneni.eventflow.order.domain.IdempotencyRecord;
import io.github.rkunameneni.eventflow.order.domain.IdempotencyRecordRepository;
import io.github.rkunameneni.eventflow.order.domain.Order;
import io.github.rkunameneni.eventflow.order.domain.OrderRepository;
import io.github.rkunameneni.eventflow.support.messaging.OutboxWriter;
import io.github.rkunameneni.eventflow.support.web.error.BusinessRuleViolationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

    private static final Instant NOW = Instant.parse("2024-06-01T10:00:00Z");
    private static final List<Order.LineItem> LINES = List.of(new Order.LineItem("SKU-1", 2, new BigDecimal("5.00")));

    @Mock
    private OrderRepository orders;
    @Mock
    private IdempotencyRecordRepository idempotencyRecords;
    @Mock
    private OutboxWriter outbox;

    private SimpleMeterRegistry meterRegistry;
    private OrderPlacementService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new OrderPlacementService(orders, idempotencyRecords, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry);
    }

    @Test
    void newOrderIsSavedWithItsIdempotencyKeyAndAnOrderCreatedEvent() {
        when(idempotencyRecords.findById(any())).thenReturn(Optional.empty());

        PlacementResult result = service.place(new PlaceOrderCommand("alice", "key-00000001", "USD", LINES));

        assertThat(result.replayed()).isFalse();
        verify(orders).save(result.order());
        verify(idempotencyRecords).saveAndFlush(any(IdempotencyRecord.class));
        ArgumentCaptor<OrderCreated> event = ArgumentCaptor.forClass(OrderCreated.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(result.order().getId());
        assertThat(event.getValue().totalAmount()).isEqualByComparingTo("10.00");
        assertThat(event.getValue().lines()).hasSize(1);
        assertThat(meterRegistry.counter("eventflow.orders.placed").count()).isEqualTo(1.0);
    }

    @Test
    void retryWithTheSameKeyAndBodyReturnsTheOriginalOrder() {
        Order original = Order.place("alice", "USD", LINES, NOW);
        var record = new IdempotencyRecord(new IdempotencyRecord.Key("alice", "key-00000001"),
                RequestFingerprint.of("USD", LINES), original.getId(), NOW);
        when(idempotencyRecords.findById(any())).thenReturn(Optional.of(record));
        when(orders.findWithItemsById(original.getId())).thenReturn(Optional.of(original));

        PlacementResult result = service.place(new PlaceOrderCommand("alice", "key-00000001", "USD", LINES));

        assertThat(result.replayed()).isTrue();
        assertThat(result.order()).isSameAs(original);
        verify(orders, never()).save(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() {
        var record = new IdempotencyRecord(new IdempotencyRecord.Key("alice", "key-00000001"),
                "fingerprint-of-another-body", Order.place("alice", "USD", LINES, NOW).getId(), NOW);
        when(idempotencyRecords.findById(any())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.place(new PlaceOrderCommand("alice", "key-00000001", "USD", LINES)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        verify(outbox, never()).append(any());
    }

    @Test
    void duplicateSkusAreRejected() {
        when(idempotencyRecords.findById(any())).thenReturn(Optional.empty());
        var lines = List.of(new Order.LineItem("SKU-1", 1, BigDecimal.ONE), new Order.LineItem("SKU-1", 2, BigDecimal.ONE));

        assertThatThrownBy(() -> service.place(new PlaceOrderCommand("alice", "key-00000002", "USD", lines)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo("DUPLICATE_SKU");
    }
}
