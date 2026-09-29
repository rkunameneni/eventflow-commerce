package io.github.rkunameneni.eventflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The customer was charged successfully. */
public record PaymentCompleted(
        UUID eventId,
        UUID orderId,
        String customerId,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        Instant occurredAt) implements SagaEvent {
}
