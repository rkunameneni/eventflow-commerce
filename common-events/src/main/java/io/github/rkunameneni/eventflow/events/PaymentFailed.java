package io.github.rkunameneni.eventflow.events;

import java.time.Instant;
import java.util.UUID;

/** The payment provider declined the charge; triggers compensation of the stock reservation. */
public record PaymentFailed(
        UUID eventId,
        UUID orderId,
        String customerId,
        String reason,
        Instant occurredAt) implements SagaEvent {
}
