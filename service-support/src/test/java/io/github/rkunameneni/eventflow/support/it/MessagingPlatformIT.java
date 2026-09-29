package io.github.rkunameneni.eventflow.support.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.events.PaymentFailed;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.support.test.Containers;
import io.github.rkunameneni.eventflow.support.test.KafkaTestClient;
import io.github.rkunameneni.eventflow.support.test.TestJwt;
import io.github.rkunameneni.eventflow.support.web.CorrelationId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PlatformTestApplication.class)
@AutoConfigureMockMvc
class MessagingPlatformIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String DLT = Topics.deadLetterTopic(Topics.PAYMENT_FAILED);

    private static KafkaTestClient kafka;

    @Autowired
    private PlatformTestApplication.ProbeHandler handler;
    @Autowired
    private PlatformTestApplication.ProbeListener listener;
    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Containers.registerPostgres(registry);
        Containers.registerKafka(registry);
        TestJwt.register(registry);
    }

    @BeforeAll
    static void startClient() {
        kafka = new KafkaTestClient(Containers.kafkaBootstrapServers(), List.of(Topics.ORDER_CONFIRMED, DLT));
    }

    @AfterAll
    static void stopClient() {
        kafka.close();
    }

    @Test
    void outboxEventsArePublishedWithTheirCorrelationId() {
        var event = new OrderConfirmed(UUID.randomUUID(), UUID.randomUUID(), "alice",
                new BigDecimal("12.50"), "USD", Instant.now());
        MDC.put(CorrelationId.MDC_KEY, "it-outbox");
        try {
            handler.emit(event);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }

        ConsumerRecord<String, String> published = kafka.awaitRecord(Topics.ORDER_CONFIRMED,
                r -> r.key().equals(event.orderId().toString()), TIMEOUT).orElseThrow();

        assertThat(published.value()).contains(event.eventId().toString());
        assertThat(new String(published.headers().lastHeader(CorrelationId.HEADER).value(), StandardCharsets.UTF_8))
                .isEqualTo("it-outbox");
    }

    @Test
    void duplicateDeliveriesAreHandledOnce() {
        var event = paymentFailed("bob");

        kafka.publish(event);
        kafka.publish(event);

        await().atMost(TIMEOUT).until(() -> listener.attempts.containsKey(event.eventId())
                && listener.attempts.get(event.eventId()).get() == 2);
        assertThat(handler.handled()).containsOnlyOnce(event.eventId());
    }

    @Test
    void failingEventsAreRetriedThenDeadLetteredAndCanBeReplayed() throws Exception {
        var event = paymentFailed(PlatformTestApplication.POISON_CUSTOMER);

        kafka.publish(event);

        kafka.awaitRecord(DLT, r -> r.value().contains(event.eventId().toString()), TIMEOUT).orElseThrow();
        assertThat(listener.attempts.get(event.eventId())).as("1 attempt + 2 retries").hasValue(3);
        assertThat(handler.handled()).doesNotContain(event.eventId());

        handler.recoverFromOutage();
        mvc.perform(post("/api/v1/admin/dlq/{topic}/replay", Topics.PAYMENT_FAILED)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetTopic").value(Topics.PAYMENT_FAILED));

        await().atMost(TIMEOUT).until(() -> handler.handled().contains(event.eventId()));
    }

    @Test
    void malformedPayloadsAreDeadLetteredWithoutRetries() {
        String key = "malformed-" + UUID.randomUUID();

        kafka.publishRaw(Topics.PAYMENT_FAILED, key, "{\"eventId\": 42", "it-malformed");

        ConsumerRecord<String, String> deadLetter =
                kafka.awaitRecord(DLT, r -> key.equals(r.key()), TIMEOUT).orElseThrow();
        assertThat(new String(deadLetter.headers().lastHeader("kafka_dlt-exception-stacktrace").value(),
                StandardCharsets.UTF_8)).contains("MalformedEventException");
    }

    @Test
    void replayIsRestrictedToAdmins() throws Exception {
        mvc.perform(post("/api/v1/admin/dlq/{topic}/replay", Topics.PAYMENT_FAILED))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/dlq/{topic}/replay", Topics.PAYMENT_FAILED)
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer("alice", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/dlq/{topic}/replay", "not-a-saga-topic")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.admin()))
                .andExpect(status().isNotFound());
    }

    private static PaymentFailed paymentFailed(String customerId) {
        return new PaymentFailed(UUID.randomUUID(), UUID.randomUUID(), customerId, "DECLINED", Instant.now());
    }
}
