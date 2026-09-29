package io.github.rkunameneni.eventflow.support.messaging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.github.rkunameneni.eventflow.events.PaymentFailed;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.support.web.CorrelationId;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    private static final Instant NOW = Instant.parse("2024-06-01T12:00:00Z");

    @Mock
    private JdbcTemplate jdbc;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void storesTheEventWithItsTopicKeyAndCurrentCorrelationId() {
        var writer = new OutboxWriter(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        var event = new PaymentFailed(UUID.randomUUID(), UUID.randomUUID(), "alice", "DECLINED", NOW);
        MDC.put(CorrelationId.MDC_KEY, "req-7");

        writer.append(event);

        verify(jdbc).update(anyString(),
                eq(event.eventId()),
                eq(event.orderId().toString()),
                eq("PaymentFailed"),
                eq(Topics.PAYMENT_FAILED),
                argThat((String json) -> json.contains("\"reason\":\"DECLINED\"")),
                eq("req-7"),
                eq(Timestamp.from(NOW)));
    }
}
