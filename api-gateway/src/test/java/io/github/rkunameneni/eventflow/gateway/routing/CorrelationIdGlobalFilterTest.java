package io.github.rkunameneni.eventflow.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @Test
    void keepsAWellFormedClientId() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                .header(CorrelationIdGlobalFilter.HEADER, "checkout-42"));

        filter.filter(exchange, chain).block();

        assertThat(forwardedId()).isEqualTo("checkout-42");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER))
                .isEqualTo("checkout-42");
    }

    @Test
    void replacesMissingOrUnsafeIds() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                .header(CorrelationIdGlobalFilter.HEADER, "<script>"));

        filter.filter(exchange, chain).block();

        assertThat(forwardedId()).matches("[0-9a-f-]{36}");
    }

    private String forwardedId() {
        return forwarded.get().getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER);
    }
}
