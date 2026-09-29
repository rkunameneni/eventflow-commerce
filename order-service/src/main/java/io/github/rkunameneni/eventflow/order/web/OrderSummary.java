package io.github.rkunameneni.eventflow.order.web;

import io.github.rkunameneni.eventflow.order.domain.Order;
import io.github.rkunameneni.eventflow.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** List view of an order; line items are only returned by the single-order endpoint. */
public record OrderSummary(
        UUID id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt) {

    public static OrderSummary from(Order order) {
        return new OrderSummary(order.getId(), order.getCustomerId(), order.getStatus(), order.getTotalAmount(),
                order.getCurrency(), order.getCreatedAt());
    }
}
