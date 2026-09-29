package io.github.rkunameneni.eventflow.events;

import java.time.Instant;
import java.util.UUID;

/** Stock could not be reserved (unknown SKU, insufficient stock or a stale quoted price). */
public record InventoryRejected(
        UUID eventId,
        UUID orderId,
        String customerId,
        String reason,
        Instant occurredAt) implements SagaEvent {
}
