package io.github.rkunameneni.eventflow.support.config;

import io.github.rkunameneni.eventflow.events.MalformedEventException;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.support.messaging.CorrelationIdRecordInterceptor;
import io.github.rkunameneni.eventflow.support.messaging.DeadLetterController;
import io.github.rkunameneni.eventflow.support.messaging.DeadLetterReplayer;
import io.github.rkunameneni.eventflow.support.messaging.OutboxRelay;
import io.github.rkunameneni.eventflow.support.messaging.OutboxWriter;
import io.github.rkunameneni.eventflow.support.messaging.ProcessedEventStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kafka and outbox infrastructure shared by every saga participant: the outbox writer/relay,
 * the processed-event store, retry-then-dead-letter error handling, topic provisioning and the
 * DLT replay endpoint.
 */
@AutoConfiguration(after = {KafkaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@EnableScheduling
@EnableConfigurationProperties(EventflowProperties.class)
public class MessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OutboxWriter outboxWriter(JdbcTemplate jdbc, Clock clock) {
        return new OutboxWriter(jdbc, clock);
    }

    @Bean
    ProcessedEventStore processedEventStore(JdbcTemplate jdbc, Clock clock) {
        return new ProcessedEventStore(jdbc, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "eventflow.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
    OutboxRelay outboxRelay(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            KafkaTemplate<String, String> kafka, EventflowProperties properties, Clock clock,
            MeterRegistry meterRegistry) {
        return new OutboxRelay(jdbc, new TransactionTemplate(transactionManager), kafka, properties, clock,
                meterRegistry);
    }

    /**
     * Retries a failing record with exponential back-off, then publishes it to {@code <topic>.DLT}.
     * Malformed payloads skip the retries: re-reading the same bytes cannot succeed.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, EventflowProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (failed, ex) -> new TopicPartition(Topics.deadLetterTopic(failed.topic()), -1));

        EventflowProperties.ConsumerRetry retry = properties.consumerRetry();
        var backOff = new ExponentialBackOffWithMaxRetries(retry.maxRetries());
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());

        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(MalformedEventException.class);
        // Each retry is expected behaviour, not an incident; the final failure is visible in the DLT.
        handler.setLogLevel(KafkaException.Level.WARN);
        return handler;
    }

    @Bean
    @ConditionalOnMissingBean
    RecordInterceptor<Object, Object> correlationIdRecordInterceptor() {
        return new CorrelationIdRecordInterceptor();
    }

    /** Declares every saga topic and its dead-letter topic so a fresh cluster needs no manual setup. */
    @Bean
    KafkaAdmin.NewTopics sagaTopics(EventflowProperties properties) {
        EventflowProperties.TopicDefaults defaults = properties.topics();
        List<NewTopic> topics = new ArrayList<>();
        for (String topic : Topics.ALL) {
            topics.add(new NewTopic(topic, defaults.partitions(), defaults.replicationFactor()));
            topics.add(new NewTopic(Topics.deadLetterTopic(topic), defaults.partitions(),
                    defaults.replicationFactor()));
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    @Bean
    DeadLetterReplayer deadLetterReplayer(ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafka, @Value("${spring.application.name}") String applicationName) {
        return new DeadLetterReplayer(consumerFactory, kafka, applicationName);
    }

    @Bean
    DeadLetterController deadLetterController(DeadLetterReplayer replayer) {
        return new DeadLetterController(replayer);
    }
}
