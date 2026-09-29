package io.github.rkunameneni.eventflow.order.domain;

/**
 * Saga state of an order as seen by its owner, the order-service.
 *
 * <pre>
 * PENDING --InventoryReserved--> INVENTORY_RESERVED --PaymentCompleted--> CONFIRMED
 *    |                                  |
 *    +--InventoryRejected / PaymentFailed--> CANCELLED
 * </pre>
 */
public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    CONFIRMED,
    CANCELLED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == CANCELLED;
    }
}
