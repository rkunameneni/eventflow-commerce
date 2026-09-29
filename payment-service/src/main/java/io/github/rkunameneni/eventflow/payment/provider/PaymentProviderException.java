package io.github.rkunameneni.eventflow.payment.provider;

/** A technical failure talking to a provider (5xx, connection reset, ...). Safe to retry. */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
