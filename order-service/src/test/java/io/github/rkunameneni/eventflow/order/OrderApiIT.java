package io.github.rkunameneni.eventflow.order;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import io.github.rkunameneni.eventflow.events.EventJson;
import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.events.OrderCreated;
import io.github.rkunameneni.eventflow.events.PaymentCompleted;
import io.github.rkunameneni.eventflow.events.PaymentFailed;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.support.test.Containers;
import io.github.rkunameneni.eventflow.support.test.KafkaTestClient;
import io.github.rkunameneni.eventflow.support.test.TestJwt;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

/** API and saga behaviour of the order-service against real Postgres and Kafka. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.name=" + OrderServiceApplication.SERVICE_NAME)
class OrderApiIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String ORDER_BODY = """
            {"currency": "USD", "lines": [
              {"sku": "SKU-KEYBOARD", "quantity": 2, "unitPrice": 79.00},
              {"sku": "SKU-MOUSE", "quantity": 1, "unitPrice": 25.50}]}
            """;

    private static KafkaTestClient kafka;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Containers.registerPostgres(registry);
        Containers.registerKafka(registry);
        TestJwt.register(registry);
        registry.add("eventflow.outbox.poll-interval-ms", () -> "100");
    }

    @BeforeAll
    static void startKafkaClient() {
        kafka = new KafkaTestClient(Containers.kafkaBootstrapServers(),
                List.of(Topics.ORDER_CREATED, Topics.ORDER_CONFIRMED, Topics.ORDER_CANCELLED));
    }

    @AfterAll
    static void stopKafkaClient() {
        kafka.close();
    }

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.basePath = "/api/v1/orders";
    }

    @Test
    void placingAnOrderReturns201AndPublishesOrderCreatedThroughTheOutbox() {
        String orderId = asCustomer("alice")
                .header("Idempotency-Key", newKey())
                .header("X-Correlation-Id", "it-place-order")
                .body(ORDER_BODY)
                .when().post()
                .then()
                .statusCode(201)
                .header("Location", startsWith("/api/v1/orders/"))
                .body("status", equalTo("PENDING"))
                .body("totalAmount", equalTo(183.5f))
                .body("items", hasSize(2))
                .extract().path("id");

        var record = kafka.awaitRecord(Topics.ORDER_CREATED, r -> r.key().equals(orderId), TIMEOUT).orElseThrow();
        OrderCreated event = EventJson.read(record.value(), OrderCreated.class);
        assertThat(event.customerId()).isEqualTo("alice");
        assertThat(event.totalAmount()).isEqualByComparingTo("183.50");
        assertThat(event.lines()).extracting("sku").containsExactly("SKU-KEYBOARD", "SKU-MOUSE");
        assertThat(new String(record.headers().lastHeader("X-Correlation-Id").value(), StandardCharsets.UTF_8))
                .isEqualTo("it-place-order");
    }

    @Test
    void retryingWithTheSameIdempotencyKeyReturnsTheOriginalOrder() {
        String key = newKey();
        String firstId = asCustomer("alice").header("Idempotency-Key", key).body(ORDER_BODY)
                .when().post().then().statusCode(201).extract().path("id");

        asCustomer("alice").header("Idempotency-Key", key).body(ORDER_BODY)
                .when().post()
                .then()
                .statusCode(200)
                .header("Idempotent-Replayed", "true")
                .body("id", equalTo(firstId));

        var created = kafka.drain(Topics.ORDER_CREATED, Duration.ofSeconds(2)).stream()
                .filter(r -> r.key().equals(firstId)).toList();
        assertThat(created).as("exactly one OrderCreated").hasSize(1);
    }

    @Test
    void reusingAnIdempotencyKeyWithADifferentBodyIsRejected() {
        String key = newKey();
        asCustomer("alice").header("Idempotency-Key", key).body(ORDER_BODY).when().post().then().statusCode(201);

        asCustomer("alice").header("Idempotency-Key", key)
                .body("""
                        {"currency": "USD", "lines": [{"sku": "SKU-MOUSE", "quantity": 5, "unitPrice": 25.50}]}
                        """)
                .when().post()
                .then()
                .statusCode(422)
                .contentType("application/problem+json")
                .body("code", equalTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void invalidRequestsAreReportedAsProblemDetails() {
        asCustomer("alice").header("Idempotency-Key", newKey())
                .body("""
                        {"currency": "usd", "lines": [{"sku": "", "quantity": 0, "unitPrice": -1}]}
                        """)
                .when().post()
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("errors.currency", notNullValue())
                .body("errors.'lines[0].quantity'", notNullValue())
                .body("errors.'lines[0].unitPrice'", notNullValue());

        asCustomer("alice").body(ORDER_BODY).when().post()
                .then().statusCode(400).body("detail", startsWith("Required header 'Idempotency-Key'"));
    }

    @Test
    void requestsWithoutAValidTokenAreRejected() {
        given().contentType(ContentType.JSON).body(ORDER_BODY).header("Idempotency-Key", newKey())
                .when().post().then().statusCode(401);
        given().header("Authorization", TestJwt.bearer("svc", "SUPPORT")).contentType(ContentType.JSON)
                .header("Idempotency-Key", newKey()).body(ORDER_BODY)
                .when().post().then().statusCode(403);
    }

    @Test
    void customersCanOnlySeeTheirOwnOrdersWhileAdminsSeeAll() {
        String orderId = asCustomer("carol").header("Idempotency-Key", newKey()).body(ORDER_BODY)
                .when().post().then().statusCode(201).extract().path("id");

        asCustomer("mallory").when().get("/{id}", orderId).then().statusCode(404);
        given().header("Authorization", TestJwt.admin()).when().get("/{id}", orderId)
                .then().statusCode(200).body("customerId", equalTo("carol"));

        asCustomer("mallory").queryParam("customerId", "carol").when().get()
                .then().statusCode(200).body("content.customerId", everyItem(equalTo("mallory")));
        given().header("Authorization", TestJwt.admin()).queryParam("customerId", "carol")
                .queryParam("size", 1).when().get()
                .then().statusCode(200)
                .body("content", hasSize(1))
                .body("size", equalTo(1))
                .body("content[0].customerId", equalTo("carol"));
    }

    @Test
    void completedPaymentConfirmsTheOrderAndDuplicatesAreIgnored() {
        UUID orderId = placeOrderFor("dave");
        var reserved = new InventoryReserved(UUID.randomUUID(), orderId, "dave", new BigDecimal("183.50"), "USD",
                Instant.now());
        var paid = new PaymentCompleted(UUID.randomUUID(), orderId, "dave", UUID.randomUUID(),
                new BigDecimal("183.50"), "USD", Instant.now());

        kafka.publish(reserved);
        kafka.publish(paid);
        kafka.publish(paid);

        kafka.awaitEvent(OrderConfirmed.class, e -> e.orderId().equals(orderId), TIMEOUT);
        awaitStatus(orderId, "dave", "CONFIRMED");
        long confirmations = kafka.drain(Topics.ORDER_CONFIRMED, Duration.ofSeconds(2)).stream()
                .filter(r -> r.key().equals(orderId.toString())).count();
        assertThat(confirmations).isEqualTo(1);
    }

    @Test
    void failedPaymentCancelsTheOrder() {
        UUID orderId = placeOrderFor("erin");

        kafka.publish(new PaymentFailed(UUID.randomUUID(), orderId, "erin", "PAYMENT_DECLINED: insufficient funds",
                Instant.now()));

        OrderCancelled cancelled = kafka.awaitEvent(OrderCancelled.class, e -> e.orderId().equals(orderId), TIMEOUT);
        assertThat(cancelled.reason()).isEqualTo("PAYMENT_DECLINED: insufficient funds");
        awaitStatus(orderId, "erin", "CANCELLED");
    }

    private UUID placeOrderFor(String customer) {
        String id = asCustomer(customer).header("Idempotency-Key", newKey()).body(ORDER_BODY)
                .when().post().then().statusCode(201).extract().path("id");
        return UUID.fromString(id);
    }

    private void awaitStatus(UUID orderId, String customer, String status) {
        await().atMost(TIMEOUT).untilAsserted(() -> asCustomer(customer).when().get("/{id}", orderId)
                .then().statusCode(200).body("status", equalTo(status)));
    }

    private static RequestSpecification asCustomer(String customer) {
        return given().header("Authorization", TestJwt.bearer(customer, "CUSTOMER")).contentType(ContentType.JSON);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
