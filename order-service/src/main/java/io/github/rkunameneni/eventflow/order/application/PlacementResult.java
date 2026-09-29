package io.github.rkunameneni.eventflow.order.application;

import io.github.rkunameneni.eventflow.order.domain.Order;

/**
 * Outcome of placing an order.
 *
 * @param replayed {@code true} when an earlier request with the same idempotency key created the order
 */
public record PlacementResult(Order order, boolean replayed) {

    static PlacementResult created(Order order) {
        return new PlacementResult(order, false);
    }

    static PlacementResult replayed(Order order) {
        return new PlacementResult(order, true);
    }
}
