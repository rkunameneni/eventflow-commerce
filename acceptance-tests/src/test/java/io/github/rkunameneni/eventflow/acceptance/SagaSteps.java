package io.github.rkunameneni.eventflow.acceptance;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.rkunameneni.eventflow.support.test.TestJwt;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Step definitions. Every step talks to the services over HTTP, exactly as a client would. */
public class SagaSteps {

    private static final Duration SAGA_TIMEOUT = Duration.ofSeconds(60);

    private final SagaEnvironment environment = SagaEnvironment.get();
    private final Map<String, BigDecimal> prices = new HashMap<>();
    private String orderId;
    private String customer;

    @Given("the payment provider approves charges up to {bigdecimal}")
    public void thePaymentProviderApprovesChargesUpTo(BigDecimal limit) {
        environment.mockPay.resetAll();
        environment.mockPay.stubFor(post("/v1/charges").atPriority(10)
                .willReturn(okJson("{\"id\":\"ch_" + UUID.randomUUID() + "\",\"status\":\"APPROVED\"}")));
        environment.mockPay.stubFor(post("/v1/charges").atPriority(1)
                .withRequestBody(matchingJsonPath("$[?(@.amount > " + limit.toPlainString() + ")]"))
                .willReturn(okJson("{\"id\":\"ch_declined\",\"status\":\"DECLINED\","
                        + "\"declineReason\":\"amount_over_limit\"}")));
    }

    @Given("the catalogue has product {string} priced {bigdecimal} USD with {int} units in stock")
    public void theCatalogueHasProduct(String sku, BigDecimal price, int units) {
        prices.put(sku, price);
        given().port(environment.inventoryPort())
                .header("Authorization", TestJwt.admin())
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Acceptance " + sku, "unitPrice", price, "currency", "USD",
                        "availableQuantity", units))
                .when().put("/api/v1/products/{sku}", sku)
                .then().statusCode(200);
    }

    @When("customer {string} orders {int} of {string}")
    public void customerOrders(String customerId, int quantity, String sku) {
        customer = customerId;
        orderId = given().port(environment.orderPort())
                .header("Authorization", TestJwt.bearer(customerId, "CUSTOMER"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body(Map.of("currency", "USD",
                        "lines", List.of(Map.of("sku", sku, "quantity", quantity, "unitPrice", prices.get(sku)))))
                .when().post("/api/v1/orders")
                .then().statusCode(201).body("status", equalTo("PENDING"))
                .extract().path("id");
    }

    @Then("the order is eventually {string}")
    public void theOrderIsEventually(String status) {
        await().atMost(SAGA_TIMEOUT).untilAsserted(() -> given().port(environment.orderPort())
                .header("Authorization", TestJwt.bearer(customer, "CUSTOMER"))
                .when().get("/api/v1/orders/{id}", orderId)
                .then().statusCode(200).body("status", equalTo(status)));
    }

    @Then("the order is eventually {string} because {string}")
    public void theOrderIsEventuallyBecause(String status, String reason) {
        theOrderIsEventually(status);
        given().port(environment.orderPort())
                .header("Authorization", TestJwt.bearer(customer, "CUSTOMER"))
                .when().get("/api/v1/orders/{id}", orderId)
                .then().body("statusReason", equalTo(reason));
    }

    @And("product {string} has {int} units available and {int} reserved")
    public void productHasStock(String sku, int available, int reserved) {
        await().atMost(SAGA_TIMEOUT).untilAsserted(() -> given().port(environment.inventoryPort())
                .header("Authorization", TestJwt.admin())
                .when().get("/api/v1/products/{sku}/stock", sku)
                .then().statusCode(200)
                .body("available", equalTo(available))
                .body("reserved", equalTo(reserved)));
    }

    @And("the payment for the order is {string}")
    public void thePaymentIs(String status) {
        await().atMost(SAGA_TIMEOUT).untilAsserted(() -> given().port(environment.paymentPort())
                .header("Authorization", TestJwt.bearer(customer, "CUSTOMER"))
                .when().get("/api/v1/payments/{orderId}", orderId)
                .then().statusCode(200).body("status", equalTo(status)));
    }

    @And("no payment was attempted for the order")
    public void noPaymentWasAttempted() {
        given().port(environment.paymentPort())
                .header("Authorization", TestJwt.admin())
                .when().get("/api/v1/payments/{orderId}", orderId)
                .then().statusCode(404);
    }

    @And("customer {string} is notified that the order was {word}")
    public void customerIsNotified(String customerId, String outcome) {
        String type = "ORDER_" + outcome.toUpperCase();
        await().atMost(SAGA_TIMEOUT).untilAsserted(() -> given().port(environment.notificationPort())
                .header("Authorization", TestJwt.bearer(customerId, "CUSTOMER"))
                .when().get("/api/v1/notifications")
                .then().statusCode(200)
                .body("content.findAll { it.orderId == '" + orderId + "' }.type", hasItem(type)));
    }
}
