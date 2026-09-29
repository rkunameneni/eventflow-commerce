package io.github.rkunameneni.eventflow.payment.provider;

/**
 * The provider could not be reached even after the in-process retries, or the circuit breaker is
 * open. The Kafka consumer retries the event with back-off and finally dead-letters it; an
 * operator replays the DLT once the provider has recovered.
 */
public class PaymentProviderUnavailableException extends RuntimeException {

    public PaymentProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
