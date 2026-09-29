package io.github.rkunameneni.eventflow.gateway.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Strangler-fig facade. While the monolith is being decomposed, every path that has not been
 * migrated to a service yet falls through to the legacy application. Migrating a capability is
 * then a routing change: add a specific route for it (higher precedence) and the monolith stops
 * receiving that traffic. Once {@code eventflow.strangler.legacy-uri} is unset, the fallback
 * disappears and unknown paths return 404.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "eventflow.strangler.legacy-uri")
public class StranglerFallbackRouteConfig {

    @Bean
    RouteLocator legacyMonolithFallback(RouteLocatorBuilder routes,
            @Value("${eventflow.strangler.legacy-uri}") String legacyUri) {
        return routes.routes()
                .route("legacy-monolith", route -> route
                        .order(Ordered.LOWEST_PRECEDENCE)
                        .path("/**")
                        .uri(legacyUri))
                .build();
    }
}
