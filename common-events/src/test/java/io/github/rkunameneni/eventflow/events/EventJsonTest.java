package io.github.rkunameneni.eventflow.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventJsonTest {

    private static final Instant NOW = Instant.parse("2024-05-01T10:15:30Z");

    @Test
    void roundTripsAnEventWithNestedLines() {
        var event = new OrderCreated(UUID.randomUUID(), UUID.randomUUID(), "alice",
                List.of(new OrderLine("SKU-1", 2, new BigDecimal("19.99"))),
                new BigDecimal("39.98"), "USD", NOW);

        String json = EventJson.write(event);

        assertThat(json).contains("\"occurredAt\":\"2024-05-01T10:15:30Z\"");
        assertThat(EventJson.read(json, OrderCreated.class)).isEqualTo(event);
    }

    @Test
    void ignoresFieldsAddedByNewerProducers() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","orderId":"%s","customerId":"bob","reason":"OUT_OF_STOCK",
                 "occurredAt":"2024-05-01T10:15:30Z","warehouse":"DUB-1"}
                """.formatted(eventId, orderId);

        InventoryRejected event = EventJson.read(json, InventoryRejected.class);

        assertThat(event).isEqualTo(new InventoryRejected(eventId, orderId, "bob", "OUT_OF_STOCK", NOW));
    }

    @Test
    void rejectsMalformedPayloadsWithANonRetryableException() {
        assertThatThrownBy(() -> EventJson.read("{not json", PaymentFailed.class))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("PaymentFailed");
    }

    @Test
    void orderLineTotalMultipliesQuantityByUnitPrice() {
        assertThat(new OrderLine("SKU-1", 3, new BigDecimal("2.50")).lineTotal())
                .isEqualByComparingTo("7.50");
    }
}
