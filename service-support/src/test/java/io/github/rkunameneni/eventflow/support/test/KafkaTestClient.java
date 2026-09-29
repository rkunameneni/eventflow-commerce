package io.github.rkunameneni.eventflow.support.test;

import io.github.rkunameneni.eventflow.events.EventJson;
import io.github.rkunameneni.eventflow.events.SagaEvent;
import io.github.rkunameneni.eventflow.events.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Plays "the rest of the system" in integration tests: publishes events a service consumes and
 * waits for the events it emits. Every record seen is buffered, so assertions can be made in any
 * order.
 */
public final class KafkaTestClient implements AutoCloseable {

    private final KafkaProducer<String, String> producer;
    private final KafkaConsumer<String, String> consumer;
    private final Map<String, List<ConsumerRecord<String, String>>> seen = new ConcurrentHashMap<>();

    public KafkaTestClient(String bootstrapServers, List<String> topicsToWatch) {
        this.producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        this.consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-client-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        this.consumer.subscribe(topicsToWatch);
    }

    public void publish(SagaEvent event) {
        publishRaw(Topics.topicFor(event), event.orderId().toString(), EventJson.write(event), "it-" + event.eventId());
    }

    public void publishRaw(String topic, String key, String payload, String correlationId) {
        var producerRecord = new ProducerRecord<>(topic, key, payload);
        producerRecord.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
        try {
            producer.send(producerRecord).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Could not publish test event to " + topic, e);
        }
    }

    public <T extends SagaEvent> T awaitEvent(Class<T> type, Predicate<T> match, Duration timeout) {
        String topic = Topics.topicFor(type);
        return awaitRecord(topic, r -> match.test(EventJson.read(r.value(), type)), timeout)
                .map(r -> EventJson.read(r.value(), type))
                .orElseThrow(() -> new AssertionError("No matching " + type.getSimpleName() + " within " + timeout));
    }

    public Optional<ConsumerRecord<String, String>> awaitRecord(String topic,
            Predicate<ConsumerRecord<String, String>> match, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        do {
            Optional<ConsumerRecord<String, String>> found = recordsOn(topic).stream().filter(match).findFirst();
            if (found.isPresent()) {
                return found;
            }
            consumer.poll(Duration.ofMillis(250))
                    .forEach(r -> seen.computeIfAbsent(r.topic(), t -> new ArrayList<>()).add(r));
        } while (Instant.now().isBefore(deadline));
        return recordsOn(topic).stream().filter(match).findFirst();
    }

    /** Polls for {@code window} and returns every record seen on {@code topic} so far. */
    public List<ConsumerRecord<String, String>> drain(String topic, Duration window) {
        awaitRecord(topic, r -> false, window);
        return recordsOn(topic);
    }

    private List<ConsumerRecord<String, String>> recordsOn(String topic) {
        return List.copyOf(seen.getOrDefault(topic, List.of()));
    }

    @Override
    public void close() {
        producer.close();
        consumer.close();
    }
}
