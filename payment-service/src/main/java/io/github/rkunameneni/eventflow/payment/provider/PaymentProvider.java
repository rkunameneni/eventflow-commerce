package io.github.rkunameneni.eventflow.payment.provider;

/**
 * Strategy for talking to a payment provider. Implementations translate a {@link ChargeRequest}
 * into the provider's API and map the answer to a {@link ChargeResult}; adding a provider means
 * adding one bean - nothing else in the service changes (open/closed principle).
 *
 * <p>Contract: business outcomes (approved/declined) are returned; technical failures that may
 * succeed on retry are thrown as {@link PaymentProviderException}.
 */
public interface PaymentProvider {

    /** Name used in configuration ({@code eventflow.payment.provider}) to select this strategy. */
    String name();

    ChargeResult charge(ChargeRequest request);
}
