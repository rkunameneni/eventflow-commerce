package io.github.rkunameneni.eventflow.gateway.routing;

import java.net.InetSocketAddress;
import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate-limit buckets for the Redis token-bucket limiter ({@code RequestRateLimiter} default
 * filter). Authenticated callers are limited per user, so one noisy client cannot starve others
 * behind the same NAT; anonymous calls (the public catalogue) are limited per client IP.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    /** Trust exactly one proxy hop (the load balancer) when reading X-Forwarded-For. */
    private static final XForwardedRemoteAddressResolver CLIENT_ADDRESS =
            XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    @Bean
    KeyResolver principalOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    InetSocketAddress address = CLIENT_ADDRESS.resolve(exchange);
                    return "ip:" + (address == null ? "unknown" : address.getAddress().getHostAddress());
                }));
    }
}
