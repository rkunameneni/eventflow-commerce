package io.github.rkunameneni.eventflow.support.messaging;

import io.github.rkunameneni.eventflow.support.config.EventflowProperties;
import io.github.rkunameneni.eventflow.support.web.CorrelationId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Read side of the transactional outbox: publishes pending rows to Kafka in insertion order.
 *
 * <p>Rows are claimed with {@code FOR UPDATE SKIP LOCKED}, so several replicas can relay
 * concurrently without publishing the same row twice in the normal case. Delivery is still
 * at-least-once (a crash between the broker ack and the commit republishes the batch), which is
 * why every consumer de-duplicates on the event id - see {@link ProcessedEventStore}.
 */
public class OutboxRelay {

    public static final String EVENT_TYPE_HEADER = "eventType";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final String CLAIM_BATCH = """
            select id, aggregate_id, event_type, topic, payload::text as payload, correlation_id
            from outbox_events
            where published_at is null
            order by position
            limit ?
            for update skip locked
            """;
    private static final String MARK_PUBLISHED = "update outbox_events set published_at = ? where id = ?";
    private static final String PURGE = "delete from outbox_events where published_at < ?";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final KafkaTemplate<String, String> kafka;
    private final EventflowProperties.Outbox settings;
    private final Clock clock;
    private final Counter published;
    private final Counter failures;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions, KafkaTemplate<String, String> kafka,
            EventflowProperties properties, Clock clock, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.kafka = kafka;
        this.settings = properties.outbox();
        this.clock = clock;
        this.published = Counter.builder("eventflow.outbox.published")
                .description("Outbox events published to Kafka").register(meterRegistry);
        this.failures = Counter.builder("eventflow.outbox.relay.failures")
                .description("Relay iterations that failed and will be retried").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${eventflow.outbox.poll-interval-ms:250}")
    public void relayPending() {
        try {
            int relayed;
            do {
                relayed = relayBatch();
            } while (relayed == settings.batchSize());
        } catch (RuntimeException e) {
            failures.increment();
            log.warn("Outbox relay failed, pending events will be retried: {}", e.getMessage());
        }
    }

    /** Publishes one batch; the claim, the sends and the "published" marks share one transaction. */
    int relayBatch() {
        Integer count = transactions.execute(status -> {
            List<OutboxRecord> batch = jdbc.query(CLAIM_BATCH, (rs, i) -> new OutboxRecord(
                    rs.getObject("id", UUID.class),
                    rs.getString("aggregate_id"),
                    rs.getString("event_type"),
                    rs.getString("topic"),
                    rs.getString("payload"),
                    rs.getString("correlation_id")), settings.batchSize());
            Timestamp now = Timestamp.from(clock.instant());
            for (OutboxRecord outboxRecord : batch) {
                send(outboxRecord);
                jdbc.update(MARK_PUBLISHED, now, outboxRecord.id());
            }
            return batch.size();
        });
        int relayed = count == null ? 0 : count;
        published.increment(relayed);
        return relayed;
    }

    @Scheduled(cron = "${eventflow.outbox.purge-cron:0 17 * * * *}")
    public void purgePublished() {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(settings.retention()));
        int purged = jdbc.update(PURGE, cutoff);
        if (purged > 0) {
            log.info("Purged {} published outbox events older than {}", purged, settings.retention());
        }
    }

    private void send(OutboxRecord outboxRecord) {
        var producerRecord = new ProducerRecord<String, String>(
                outboxRecord.topic(), outboxRecord.aggregateId(), outboxRecord.payload());
        producerRecord.headers().add(EVENT_TYPE_HEADER, outboxRecord.eventType().getBytes(StandardCharsets.UTF_8));
        if (outboxRecord.correlationId() != null) {
            producerRecord.headers().add(CorrelationId.HEADER,
                    outboxRecord.correlationId().getBytes(StandardCharsets.UTF_8));
        }
        try {
            kafka.send(producerRecord).get(settings.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + outboxRecord.id(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not publish outbox event " + outboxRecord.id(), e);
        }
    }

    private record OutboxRecord(UUID id, String aggregateId, String eventType, String topic, String payload,
                                String correlationId) {
    }
}
