package io.github.rkunameneni.eventflow.payment.provider;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * Decorator that guards the active provider with Resilience4j. Order of decoration, outermost
 * first:
 *
 * <ol>
 *   <li><b>Retry</b> with exponential back-off, for transient faults;</li>
 *   <li><b>CircuitBreaker</b>, which fails fast while the provider is unhealthy so retries do not
 *       pile onto an outage (every attempt counts towards the failure rate);</li>
 *   <li><b>TimeLimiter</b>, which bounds each individual call.</li>
 * </ol>
 *
 * Declines are normal results and neither retried nor counted as failures.
 */
public class ResilientPaymentGateway {

    private final PaymentProvider provider;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    public ResilientPaymentGateway(PaymentProvider provider, Retry retry, CircuitBreaker circuitBreaker,
            TimeLimiter timeLimiter, ExecutorService executor) {
        this.provider = provider;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.timeLimiter = timeLimiter;
        this.executor = executor;
    }

    public String providerName() {
        return provider.name();
    }

    public ChargeResult charge(ChargeRequest request) {
        Callable<ChargeResult> timeLimited = TimeLimiter.decorateFutureSupplier(timeLimiter,
                () -> CompletableFuture.supplyAsync(() -> provider.charge(request), executor));
        Callable<ChargeResult> guarded = CircuitBreaker.decorateCallable(circuitBreaker, timeLimited);
        Callable<ChargeResult> resilient = Retry.decorateCallable(retry, guarded);
        try {
            return resilient.call();
        } catch (CallNotPermittedException e) {
            throw new PaymentProviderUnavailableException("Circuit breaker for " + provider.name() + " is open", e);
        } catch (PaymentProviderException | TimeoutException e) {
            throw new PaymentProviderUnavailableException(
                    provider.name() + " failed after " + retry.getRetryConfig().getMaxAttempts() + " attempts", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected checked exception from " + provider.name(), e);
        }
    }
}
