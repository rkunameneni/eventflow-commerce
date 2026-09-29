package io.github.rkunameneni.eventflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Every order line was reserved; carries what the payment step needs to charge the customer. */
public record InventoryReserved(
        UUID eventId,
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        String currency,
        Instant occurredAt) implements SagaEvent {
}
