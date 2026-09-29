package io.github.rkunameneni.eventflow.support.messaging;

import io.github.rkunameneni.eventflow.events.Topics;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * Moves messages from a dead-letter topic back onto the topic they originally failed on - the
 * operator's tool once the cause (a provider outage, a bug fix) has been dealt with.
 *
 * <p>Progress is tracked with committed offsets of a dedicated consumer group, so repeated calls
 * only replay messages that arrived in the DLT since the previous replay.
 */
public class DeadLetterReplayer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterReplayer.class);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
    private static final int MAX_EMPTY_POLLS = 2;

    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafka;
    private final String replayGroupId;

    public DeadLetterReplayer(ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafka,
            String applicationName) {
        this.consumerFactory = consumerFactory;
        this.kafka = kafka;
        this.replayGroupId = applicationName + "-dlt-replay";
    }

    public ReplayResult replay(String topic, int maxMessages) {
        String deadLetterTopic = Topics.deadLetterTopic(topic);
        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        overrides.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Math.min(maxMessages, 500));

        int replayed = 0;
        try (Consumer<String, String> consumer =
                     consumerFactory.createConsumer(replayGroupId, "dlt-replay", null, overrides)) {
            List<PartitionInfo> partitions = consumer.partitionsFor(deadLetterTopic);
            if (partitions == null || partitions.isEmpty()) {
                return new ReplayResult(deadLetterTopic, topic, 0);
            }
            // Manual assignment avoids waiting for a consumer-group rebalance on every call.
            consumer.assign(partitions.stream().map(p -> new TopicPartition(p.topic(), p.partition())).toList());

            int emptyPolls = 0;
            while (replayed < maxMessages && emptyPolls < MAX_EMPTY_POLLS) {
                var records = consumer.poll(POLL_TIMEOUT);
                if (records.isEmpty()) {
                    emptyPolls++;
                    continue;
                }
                Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
                for (ConsumerRecord<String, String> deadLetter : records) {
                    if (replayed == maxMessages) {
                        break;
                    }
                    republish(topic, deadLetter);
                    offsets.put(new TopicPartition(deadLetter.topic(), deadLetter.partition()),
                            new OffsetAndMetadata(deadLetter.offset() + 1));
                    replayed++;
                }
                consumer.commitSync(offsets);
            }
        }
        log.info("Replayed {} message(s) from {} to {}", replayed, deadLetterTopic, topic);
        return new ReplayResult(deadLetterTopic, topic, replayed);
    }

    private void republish(String topic, ConsumerRecord<String, String> deadLetter) {
        RecordHeaders headers = new RecordHeaders();
        for (Header header : deadLetter.headers()) {
            // Drop the diagnostics added by the dead-letter recoverer; keep correlation/trace headers.
            if (!header.key().startsWith(KafkaHeaders.PREFIX + "dlt-")) {
                headers.add(header);
            }
        }
        try {
            kafka.send(new ProducerRecord<>(topic, null, deadLetter.key(), deadLetter.value(), headers))
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while replaying to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not replay dead letter to " + topic, e);
        }
    }

    public record ReplayResult(String deadLetterTopic, String targetTopic, int replayed) {
    }
}
