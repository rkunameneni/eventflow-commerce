package io.github.rkunameneni.eventflow.order.web;

import io.github.rkunameneni.eventflow.order.domain.Order;
import io.github.rkunameneni.eventflow.order.domain.OrderItem;
import io.github.rkunameneni.eventflow.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus status,
        String statusReason,
        BigDecimal totalAmount,
        String currency,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(), order.getStatusReason(),
                order.getTotalAmount(), order.getCurrency(), order.getItems().stream().map(Item::from).toList(),
                order.getCreatedAt(), order.getUpdatedAt());
    }

    public record Item(String sku, int quantity, BigDecimal unitPrice) {

        static Item from(OrderItem item) {
            return new Item(item.getSku(), item.getQuantity(), item.getUnitPrice());
        }
    }
}
