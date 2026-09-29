package io.github.rkunameneni.eventflow.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResilientPaymentGatewayTest {

    private static final ChargeRequest REQUEST =
            new ChargeRequest("pay-1", UUID.randomUUID(), "alice", new BigDecimal("42.00"), "USD");

    @Mock
    private PaymentProvider provider;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private CircuitBreaker circuitBreaker;
    private ResilientPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        Retry retry = Retry.of("test", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(PaymentProviderException.class, TimeoutException.class)
                .build());
        circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordExceptions(PaymentProviderException.class, TimeoutException.class)
                .build());
        TimeLimiter timeLimiter = TimeLimiter.of(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(200))
                .build());
        gateway = new ResilientPaymentGateway(provider, retry, circuitBreaker, timeLimiter, executor);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void transientFailuresAreRetried() {
        when(provider.charge(any()))
                .thenThrow(new PaymentProviderException("503"))
                .thenReturn(new ChargeResult.Approved("ch_1"));

        assertThat(gateway.charge(REQUEST)).isEqualTo(new ChargeResult.Approved("ch_1"));
        verify(provider, times(2)).charge(REQUEST);
    }

    @Test
    void declinesAreReturnedWithoutRetrying() {
        when(provider.charge(any())).thenReturn(new ChargeResult.Declined("insufficient_funds"));

        assertThat(gateway.charge(REQUEST)).isEqualTo(new ChargeResult.Declined("insufficient_funds"));
        verify(provider, times(1)).charge(REQUEST);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void slowCallsTimeOutAndAreRetriedBeforeGivingUp() {
        when(provider.charge(any())).thenAnswer(invocation -> {
            Thread.sleep(1_000);
            return new ChargeResult.Approved("too-late");
        });

        assertThatThrownBy(() -> gateway.charge(REQUEST))
                .isInstanceOf(PaymentProviderUnavailableException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        verify(provider, times(3)).charge(REQUEST);
    }

    @Test
    void theCircuitOpensAfterRepeatedFailuresAndThenFailsFast() {
        when(provider.charge(any())).thenThrow(new PaymentProviderException("503"));

        // The 4th failed attempt fills the sliding window (100% failures) and opens the circuit;
        // the remaining attempts of the second call are rejected without reaching the provider.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> gateway.charge(REQUEST)).isInstanceOf(PaymentProviderUnavailableException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> gateway.charge(REQUEST))
                .isInstanceOf(PaymentProviderUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        verify(provider, times(4)).charge(REQUEST);
    }
}
