package io.github.rkunameneni.eventflow.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Common shape of every event exchanged in the order-fulfilment saga.
 *
 * <p>The hierarchy is sealed so that adding a new event type is a deliberate, compile-checked
 * change: every {@code switch} over saga events must then handle it.
 */
public sealed interface SagaEvent
        permits OrderCreated, InventoryReserved, InventoryRejected, PaymentCompleted, PaymentFailed,
                OrderConfirmed, OrderCancelled {

    /** Unique id of this event instance; consumers use it for de-duplication. */
    UUID eventId();

    /** Id of the order (saga instance) the event belongs to; also used as the Kafka message key. */
    UUID orderId();

    /** Customer that owns the order. */
    String customerId();

    Instant occurredAt();
}
