package io.github.rkunameneni.eventflow.order.application;

import io.github.rkunameneni.eventflow.events.InventoryRejected;
import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.events.PaymentCompleted;
import io.github.rkunameneni.eventflow.events.PaymentFailed;
import io.github.rkunameneni.eventflow.events.SagaEvent;
import io.github.rkunameneni.eventflow.order.domain.Order;
import io.github.rkunameneni.eventflow.order.domain.OrderRepository;
import io.github.rkunameneni.eventflow.support.messaging.OutboxWriter;
import io.github.rkunameneni.eventflow.support.messaging.ProcessedEventStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The order-service's part of the choreographed saga: it reacts to the other participants'
 * outcomes and publishes the terminal {@code OrderConfirmed} / {@code OrderCancelled} events.
 * Each handler runs in one transaction covering the de-duplication marker, the state change and
 * the outbox row.
 */
@Service
public class OrderSagaHandler {

    static final String CONSUMER = "order-service";

    private static final Logger log = LoggerFactory.getLogger(OrderSagaHandler.class);

    private final OrderRepository orders;
    private final ProcessedEventStore processedEvents;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public OrderSagaHandler(OrderRepository orders, ProcessedEventStore processedEvents, OutboxWriter outbox,
            Clock clock, MeterRegistry meterRegistry) {
        this.orders = orders;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void on(InventoryReserved event) {
        apply(event, (order, now) -> order.markInventoryReserved(now));
    }

    @Transactional
    public void on(InventoryRejected event) {
        apply(event, (order, now) -> cancel(order, event.reason(), now));
    }

    @Transactional
    public void on(PaymentCompleted event) {
        apply(event, (order, now) -> {
            if (order.confirm(now)) {
                outbox.append(new OrderConfirmed(UUID.randomUUID(), order.getId(), order.getCustomerId(),
                        order.getTotalAmount(), order.getCurrency(), now));
                meterRegistry.counter("eventflow.orders.completed", "outcome", "confirmed").increment();
            }
        });
    }

    @Transactional
    public void on(PaymentFailed event) {
        apply(event, (order, now) -> cancel(order, event.reason(), now));
    }

    private void cancel(Order order, String reason, Instant now) {
        if (order.cancel(reason, now)) {
            outbox.append(new OrderCancelled(UUID.randomUUID(), order.getId(), order.getCustomerId(), reason, now));
            meterRegistry.counter("eventflow.orders.completed", "outcome", "cancelled").increment();
        }
    }

    private void apply(SagaEvent event, BiConsumer<Order, Instant> transition) {
        if (!processedEvents.markProcessed(event.eventId(), CONSUMER)) {
            log.info("Skipping duplicate {} {}", event.getClass().getSimpleName(), event.eventId());
            return;
        }
        // An unknown order is treated as transient (e.g. replica lag) and retried, then dead-lettered.
        Order order = orders.findById(event.orderId())
                .orElseThrow(() -> new IllegalStateException("Unknown order " + event.orderId()));
        transition.accept(order, clock.instant());
        log.info("Order {} is {} after {}", order.getId(), order.getStatus(), event.getClass().getSimpleName());
    }
}
