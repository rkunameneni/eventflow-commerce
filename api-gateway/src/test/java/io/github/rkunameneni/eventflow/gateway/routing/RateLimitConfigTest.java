package io.github.rkunameneni.eventflow.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RateLimitConfigTest {

    private final KeyResolver resolver = new RateLimitConfig().principalOrIpKeyResolver();

    @Test
    void authenticatedCallersAreLimitedPerUser() {
        Principal alice = () -> "alice";
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"))
                .mutate().principal(Mono.just(alice)).build();

        assertThat(resolver.resolve(exchange).block()).isEqualTo("user:alice");
    }

    @Test
    void anonymousCallersAreLimitedPerClientAddressSeenByTheLoadBalancer() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/products")
                .header("X-Forwarded-For", "203.0.113.7")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 443)));

        assertThat(resolver.resolve(exchange).block()).isEqualTo("ip:203.0.113.7");
    }
}
