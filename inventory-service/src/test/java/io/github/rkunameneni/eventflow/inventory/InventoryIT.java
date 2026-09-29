package io.github.rkunameneni.eventflow.inventory;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import io.github.rkunameneni.eventflow.events.InventoryRejected;
import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.events.OrderCreated;
import io.github.rkunameneni.eventflow.events.OrderLine;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.support.test.Containers;
import io.github.rkunameneni.eventflow.support.test.KafkaTestClient;
import io.github.rkunameneni.eventflow.support.test.TestJwt;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Catalogue caching and the reservation saga step against real Postgres, Redis and Kafka. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.name=" + InventoryServiceApplication.SERVICE_NAME)
class InventoryIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static KafkaTestClient kafka;

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redis;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Containers.registerPostgres(registry);
        Containers.registerKafka(registry);
        Containers.registerRedis(registry);
        TestJwt.register(registry);
        registry.add("eventflow.outbox.poll-interval-ms", () -> "100");
    }

    @BeforeAll
    static void startKafkaClient() {
        kafka = new KafkaTestClient(Containers.kafkaBootstrapServers(),
                List.of(Topics.INVENTORY_RESERVED, Topics.INVENTORY_REJECTED));
    }

    @AfterAll
    static void stopKafkaClient() {
        kafka.close();
    }

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.basePath = "/api/v1/products";
    }

    @Test
    void productLookupsAreCachedInRedisAndEvictedOnUpdate() {
        String sku = newSku();
        upsert(sku, "19.99", 10);

        given().when().get("/{sku}", sku).then().statusCode(200).body("unitPrice", equalTo(19.99f));
        assertThat(redis.opsForValue().get("inventory::products::" + sku)).contains("\"unitPrice\":19.99");

        upsert(sku, "24.99", 10);

        assertThat(redis.hasKey("inventory::products::" + sku)).as("evicted by the update").isFalse();
        given().when().get("/{sku}", sku).then().statusCode(200).body("unitPrice", equalTo(24.99f));
    }

    @Test
    void theCatalogueIsPublicButChangesRequireAdmin() {
        given().when().get().then().statusCode(200);
        given().contentType(ContentType.JSON).body(productBody("1.00", 1))
                .when().put("/{sku}", newSku()).then().statusCode(401);
        given().header("Authorization", TestJwt.bearer("alice", "CUSTOMER")).contentType(ContentType.JSON)
                .body(productBody("1.00", 1)).when().put("/{sku}", newSku()).then().statusCode(403);
        given().when().get("/{sku}", "SKU-DOES-NOT-EXIST").then().statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    void anOrderThatFitsIsReservedExactlyOnceEvenIfDeliveredTwice() {
        String sku = newSku();
        upsert(sku, "10.00", 5);
        OrderCreated order = order(sku, 3, "10.00");

        kafka.publish(order);
        kafka.publish(order);

        InventoryReserved reserved = kafka.awaitEvent(InventoryReserved.class,
                e -> e.orderId().equals(order.orderId()), TIMEOUT);
        assertThat(reserved.totalAmount()).isEqualByComparingTo("30.00");
        awaitStock(sku, 2, 3);
        long reservations = kafka.drain(Topics.INVENTORY_RESERVED, Duration.ofSeconds(2)).stream()
                .filter(r -> r.key().equals(order.orderId().toString())).count();
        assertThat(reservations).isEqualTo(1);
    }

    @Test
    void anOrderThatDoesNotFitIsRejectedAndStockIsUntouched() {
        String sku = newSku();
        upsert(sku, "10.00", 2);
        OrderCreated order = order(sku, 3, "10.00");

        kafka.publish(order);

        InventoryRejected rejected = kafka.awaitEvent(InventoryRejected.class,
                e -> e.orderId().equals(order.orderId()), TIMEOUT);
        assertThat(rejected.reason()).isEqualTo("INSUFFICIENT_STOCK: " + sku);
        awaitStock(sku, 2, 0);
    }

    @Test
    void cancellationReleasesStockAndConfirmationCommitsIt() {
        String sku = newSku();
        upsert(sku, "10.00", 10);
        OrderCreated cancelledOrder = order(sku, 4, "10.00");
        OrderCreated confirmedOrder = order(sku, 1, "10.00");
        kafka.publish(cancelledOrder);
        kafka.publish(confirmedOrder);
        awaitStock(sku, 5, 5);

        kafka.publish(new OrderCancelled(UUID.randomUUID(), cancelledOrder.orderId(), "alice", "PAYMENT_DECLINED",
                Instant.now()));
        kafka.publish(new OrderConfirmed(UUID.randomUUID(), confirmedOrder.orderId(), "alice", BigDecimal.TEN,
                "USD", Instant.now()));

        awaitStock(sku, 9, 0);
    }

    private void awaitStock(String sku, int available, int reserved) {
        await().atMost(TIMEOUT).untilAsserted(() -> given()
                .header("Authorization", TestJwt.bearer("alice", "CUSTOMER"))
                .when().get("/{sku}/stock", sku)
                .then().statusCode(200)
                .body("available", equalTo(available))
                .body("reserved", equalTo(reserved)));
    }

    private static void upsert(String sku, String price, int available) {
        given().header("Authorization", TestJwt.admin()).contentType(ContentType.JSON)
                .body(productBody(price, available))
                .when().put("/{sku}", sku)
                .then().statusCode(200);
    }

    private static Map<String, Object> productBody(String price, int available) {
        return Map.of("name", "Test product", "unitPrice", new BigDecimal(price), "currency", "USD",
                "availableQuantity", available);
    }

    private static OrderCreated order(String sku, int quantity, String price) {
        var line = new OrderLine(sku, quantity, new BigDecimal(price));
        return new OrderCreated(UUID.randomUUID(), UUID.randomUUID(), "alice", List.of(line), line.lineTotal(),
                "USD", Instant.now());
    }

    private static String newSku() {
        return "SKU-IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
