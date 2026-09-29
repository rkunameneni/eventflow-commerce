package io.github.rkunameneni.eventflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Starts the saga: an order was accepted and is waiting for stock and payment. */
public record OrderCreated(
        UUID eventId,
        UUID orderId,
        String customerId,
        List<OrderLine> lines,
        BigDecimal totalAmount,
        String currency,
        Instant occurredAt) implements SagaEvent {

    public OrderCreated {
        lines = List.copyOf(lines);
    }
}
