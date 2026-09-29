package io.github.rkunameneni.eventflow.payment.provider;

/** Business outcome of a charge. Sealed, so callers handle both cases exhaustively. */
public sealed interface ChargeResult {

    record Approved(String providerReference) implements ChargeResult {
    }

    record Declined(String reason) implements ChargeResult {
    }
}
