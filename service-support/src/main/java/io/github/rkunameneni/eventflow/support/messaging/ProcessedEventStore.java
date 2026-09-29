package io.github.rkunameneni.eventflow.support.messaging;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent-consumer support. Kafka delivers at-least-once, so a handler records the event id in
 * {@code processed_events} inside its own business transaction: if the handler's work rolls back,
 * so does the marker, and the redelivered event is processed again; if it commits, any duplicate
 * delivery is recognised and skipped.
 */
public class ProcessedEventStore {

    private static final String MARK = """
            insert into processed_events (event_id, consumer, processed_at) values (?, ?, ?)
            on conflict (event_id, consumer) do nothing
            """;
    private static final String EXISTS =
            "select exists (select 1 from processed_events where event_id = ? and consumer = ?)";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProcessedEventStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Records that {@code consumer} handled {@code eventId}.
     *
     * @return {@code true} the first time, {@code false} if the event was already processed
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(UUID eventId, String consumer) {
        return jdbc.update(MARK, eventId, consumer, Timestamp.from(clock.instant())) == 1;
    }

    public boolean isProcessed(UUID eventId, String consumer) {
        return Boolean.TRUE.equals(jdbc.queryForObject(EXISTS, Boolean.class, eventId, consumer));
    }
}
