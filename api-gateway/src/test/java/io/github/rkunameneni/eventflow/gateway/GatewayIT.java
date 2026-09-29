package io.github.rkunameneni.eventflow.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Routing, edge authentication and Redis-backed rate limiting, with WireMock standing in for the services. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIT {

    private static final WireMockServer BACKEND = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        BACKEND.start();
        REDIS.start();
    }

    @Autowired
    private WebTestClient client;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("eventflow.security.jwt-secret", () -> "gateway-integration-test-key-0123456789abcdef");
        registry.add("eventflow.security.demo-issuer.enabled", () -> "true");
        registry.add("ORDER_SERVICE_URI", BACKEND::baseUrl);
        registry.add("INVENTORY_SERVICE_URI", BACKEND::baseUrl);
        registry.add("RATE_LIMIT_REPLENISH_RATE", () -> "1");
        registry.add("RATE_LIMIT_BURST_CAPACITY", () -> "3");
        registry.add("eventflow.strangler.legacy-uri", BACKEND::baseUrl);
    }

    @AfterAll
    static void stop() {
        BACKEND.stop();
        REDIS.stop();
    }

    @Test
    void protectedRoutesRequireAToken() {
        client.get().uri("/api/v1/orders").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void demoCredentialsAreExchangedForAToken() {
        client.post().uri("/api/v1/auth/token").bodyValue(Map.of("username", "alice", "password", "wrong"))
                .exchange().expectStatus().isUnauthorized();

        client.post().uri("/api/v1/auth/token").bodyValue(Map.of("username", "alice", "password", "alice-password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.accessToken").isNotEmpty();
    }

    @Test
    void authenticatedRequestsAreProxiedWithTokenAndCorrelationId() {
        BACKEND.stubFor(get("/api/v1/orders/42").willReturn(okJson("{\"id\":\"42\"}")));

        client.get().uri("/api/v1/orders/42")
                .header(HttpHeaders.AUTHORIZATION, bearer("bob", "bob-password"))
                .header("X-Correlation-Id", "gw-it-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "gw-it-1")
                .expectBody().jsonPath("$.id").isEqualTo("42");

        BACKEND.verify(getRequestedFor(urlEqualTo("/api/v1/orders/42"))
                .withHeader(HttpHeaders.AUTHORIZATION, matching("Bearer .+"))
                .withHeader("X-Correlation-Id", equalTo("gw-it-1")));
    }

    @Test
    void theCatalogueIsPublicButAdminPathsNeedTheAdminRole() {
        BACKEND.stubFor(get(urlPathMatching("/api/v1/products.*")).willReturn(okJson("{\"content\":[]}")));

        client.get().uri("/api/v1/products").exchange().expectStatus().isOk();
        client.post().uri("/api/v1/admin/orders/dlq/eventflow.order.created.v1/replay")
                .header(HttpHeaders.AUTHORIZATION, bearer("alice", "alice-password"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void unmigratedPathsFallThroughToTheLegacyMonolith() {
        BACKEND.stubFor(get("/legacy/reports/daily").willReturn(okJson("{\"source\":\"monolith\"}")));

        client.get().uri("/legacy/reports/daily")
                .header(HttpHeaders.AUTHORIZATION, bearer("bob", "bob-password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.source").isEqualTo("monolith");
    }

    @Test
    void eachUserIsRateLimitedIndependently() {
        BACKEND.stubFor(get("/api/v1/orders").willReturn(okJson("{\"content\":[]}")));
        String token = bearer("admin", "admin-password");
        WebClient http = WebClient.create("http://localhost:" + port);

        // Ten concurrent requests against a bucket of 3 tokens refilled at 1 token/s.
        List<HttpStatusCode> statuses = Flux.range(0, 10)
                .flatMap(i -> http.get().uri("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .exchangeToMono(response -> Mono.just(response.statusCode())), 10)
                .collectList()
                .block(Duration.ofSeconds(30));

        assertThat(statuses).filteredOn(status -> status.value() == 429).hasSizeGreaterThanOrEqualTo(3);
        assertThat(statuses).filteredOn(HttpStatusCode::is2xxSuccessful).isNotEmpty();
        client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer("bob", "bob-password"))
                .exchange().expectStatus().isOk();
    }

    private String bearer(String username, String password) {
        Map<?, ?> response = client.post().uri("/api/v1/auth/token")
                .bodyValue(Map.of("username", username, "password", password))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        return "Bearer " + response.get("accessToken");
    }
}
