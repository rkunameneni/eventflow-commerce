package io.github.rkunameneni.eventflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Terminal success state of the saga. */
public record OrderConfirmed(
        UUID eventId,
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        String currency,
        Instant occurredAt) implements SagaEvent {
}
