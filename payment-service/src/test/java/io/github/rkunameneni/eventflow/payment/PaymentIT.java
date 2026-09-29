package io.github.rkunameneni.eventflow.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.PaymentCompleted;
import io.github.rkunameneni.eventflow.events.PaymentFailed;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.support.test.Containers;
import io.github.rkunameneni.eventflow.support.test.KafkaTestClient;
import io.github.rkunameneni.eventflow.support.test.TestJwt;
import io.restassured.RestAssured;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The payment step end to end: Kafka in, HTTP to the (WireMock) provider, Kafka out - including
 * the outage path where the event is dead-lettered and later replayed by an operator.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.name=" + PaymentServiceApplication.SERVICE_NAME)
class PaymentIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final WireMockServer MOCK_PAY = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    private static KafkaTestClient kafka;

    @LocalServerPort
    private int port;

    static {
        MOCK_PAY.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Containers.registerPostgres(registry);
        Containers.registerKafka(registry);
        TestJwt.register(registry);
        registry.add("eventflow.payment.provider", () -> "mockpay");
        registry.add("eventflow.payment.mockpay.base-url", MOCK_PAY::baseUrl);
        registry.add("eventflow.outbox.poll-interval-ms", () -> "100");
        registry.add("eventflow.consumer-retry.max-retries", () -> "1");
        registry.add("eventflow.consumer-retry.initial-interval", () -> "50ms");
        registry.add("resilience4j.retry.instances.payment-provider.wait-duration", () -> "20ms");
        registry.add("resilience4j.circuitbreaker.instances.payment-provider.minimum-number-of-calls", () -> "100");
    }

    @BeforeAll
    static void startKafkaClient() {
        kafka = new KafkaTestClient(Containers.kafkaBootstrapServers(), List.of(Topics.PAYMENT_COMPLETED,
                Topics.PAYMENT_FAILED, Topics.deadLetterTopic(Topics.INVENTORY_RESERVED)));
    }

    @AfterAll
    static void stop() {
        kafka.close();
        MOCK_PAY.stop();
    }

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void approvedChargeCompletesThePayment() {
        InventoryReserved reserved = reservedOrder("alice", "42.00");
        stubCharge(reserved.orderId(), okJson("{\"id\":\"ch_ok\",\"status\":\"APPROVED\"}"));

        kafka.publish(reserved);

        PaymentCompleted completed = kafka.awaitEvent(PaymentCompleted.class,
                e -> e.orderId().equals(reserved.orderId()), TIMEOUT);
        assertThat(completed.amount()).isEqualByComparingTo("42.00");
        given().header("Authorization", TestJwt.bearer("alice", "CUSTOMER"))
                .when().get("/api/v1/payments/{orderId}", reserved.orderId())
                .then().statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("providerReference", equalTo("ch_ok"));
        given().header("Authorization", TestJwt.bearer("mallory", "CUSTOMER"))
                .when().get("/api/v1/payments/{orderId}", reserved.orderId())
                .then().statusCode(404);
    }

    @Test
    void declinedChargeFailsThePayment() {
        InventoryReserved reserved = reservedOrder("bob", "5000.00");
        stubCharge(reserved.orderId(),
                okJson("{\"id\":\"ch_no\",\"status\":\"DECLINED\",\"declineReason\":\"limit_exceeded\"}"));

        kafka.publish(reserved);

        PaymentFailed failed = kafka.awaitEvent(PaymentFailed.class,
                e -> e.orderId().equals(reserved.orderId()), TIMEOUT);
        assertThat(failed.reason()).isEqualTo("PAYMENT_DECLINED: limit_exceeded");
    }

    @Test
    void anOutageDeadLettersTheEventWhichCanBeReplayedAfterRecovery() {
        InventoryReserved reserved = reservedOrder("carol", "99.00");
        stubCharge(reserved.orderId(), aResponse().withStatus(503));

        kafka.publish(reserved);

        kafka.awaitRecord(Topics.deadLetterTopic(Topics.INVENTORY_RESERVED),
                r -> r.key().equals(reserved.orderId().toString()), TIMEOUT).orElseThrow();
        // 2 Kafka deliveries x 3 Resilience4j attempts, all with the same idempotency key.
        MOCK_PAY.verify(6, postRequestedFor(urlEqualTo("/v1/charges"))
                .withRequestBody(matchingJsonPath("$.reference", WireMock.equalTo(reserved.orderId().toString()))));

        stubCharge(reserved.orderId(), okJson("{\"id\":\"ch_late\",\"status\":\"APPROVED\"}"));
        given().header("Authorization", TestJwt.admin())
                .when().post("/api/v1/admin/payments/dlq/{topic}/replay", Topics.INVENTORY_RESERVED)
                .then().statusCode(200);

        kafka.awaitEvent(PaymentCompleted.class, e -> e.orderId().equals(reserved.orderId()), TIMEOUT);
        await().atMost(TIMEOUT).untilAsserted(() -> given().header("Authorization", TestJwt.admin())
                .when().get("/api/v1/payments/{orderId}", reserved.orderId())
                .then().statusCode(200).body("providerReference", equalTo("ch_late")));
    }

    private static void stubCharge(UUID orderId, ResponseDefinitionBuilder response) {
        MOCK_PAY.stubFor(post("/v1/charges")
                .withRequestBody(matchingJsonPath("$.reference", WireMock.equalTo(orderId.toString())))
                .willReturn(response));
    }

    private static InventoryReserved reservedOrder(String customer, String amount) {
        return new InventoryReserved(UUID.randomUUID(), UUID.randomUUID(), customer, new BigDecimal(amount), "USD",
                Instant.now());
    }
}
