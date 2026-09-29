package io.github.rkunameneni.eventflow.support.messaging;

import io.github.rkunameneni.eventflow.events.EventJson;
import io.github.rkunameneni.eventflow.events.SagaEvent;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.support.web.CorrelationId;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of the transactional outbox.
 *
 * <p>Events are inserted into {@code outbox_events} in the <em>same</em> database transaction as
 * the state change that caused them, so a state change and its event are committed or rolled back
 * together - there is no window in which one exists without the other. {@link OutboxRelay}
 * publishes the rows to Kafka afterwards.
 */
public class OutboxWriter {

    private static final String INSERT = """
            insert into outbox_events (id, aggregate_id, event_type, topic, payload, correlation_id, created_at)
            values (?, ?, ?, ?, ?::jsonb, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OutboxWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Must run inside the caller's transaction; fails fast if there is none. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(SagaEvent event) {
        jdbc.update(INSERT,
                event.eventId(),
                event.orderId().toString(),
                event.getClass().getSimpleName(),
                Topics.topicFor(event),
                EventJson.write(event),
                CorrelationId.current().orElse(null),
                Timestamp.from(clock.instant()));
    }
}
