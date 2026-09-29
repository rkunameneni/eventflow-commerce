package io.github.rkunameneni.eventflow.events;

import java.time.Instant;
import java.util.UUID;

/** Terminal failure state of the saga; participants release anything they still hold. */
public record OrderCancelled(
        UUID eventId,
        UUID orderId,
        String customerId,
        String reason,
        Instant occurredAt) implements SagaEvent {
}
