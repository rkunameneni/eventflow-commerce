package io.github.rkunameneni.eventflow.payment.provider;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the resilient gateway around the provider selected by {@code eventflow.payment.provider}.
 * Retry, circuit-breaker and time-limiter settings live in the {@code resilience4j.*} properties
 * under the instance name {@value #RESILIENCE_INSTANCE}, and their metrics are exported to
 * Prometheus by the Resilience4j Spring Boot starter.
 */
@Configuration(proxyBeanMethods = false)
public class PaymentGatewayConfig {

    public static final String RESILIENCE_INSTANCE = "payment-provider";

    /** Provider calls block on I/O, which is exactly what virtual threads are cheap for. */
    @Bean(destroyMethod = "close")
    ExecutorService paymentProviderExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ResilientPaymentGateway resilientPaymentGateway(PaymentProviderFactory factory, PaymentProperties properties,
            RetryRegistry retries, CircuitBreakerRegistry circuitBreakers, TimeLimiterRegistry timeLimiters,
            ExecutorService paymentProviderExecutor) {
        return new ResilientPaymentGateway(
                factory.get(properties.provider()),
                retries.retry(RESILIENCE_INSTANCE),
                circuitBreakers.circuitBreaker(RESILIENCE_INSTANCE),
                timeLimiters.timeLimiter(RESILIENCE_INSTANCE),
                paymentProviderExecutor);
    }
}
