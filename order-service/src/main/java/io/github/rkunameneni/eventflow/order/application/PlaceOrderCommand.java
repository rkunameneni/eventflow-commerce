package io.github.rkunameneni.eventflow.order.application;

import io.github.rkunameneni.eventflow.order.domain.Order;
import java.util.List;

public record PlaceOrderCommand(String customerId, String idempotencyKey, String currency, List<Order.LineItem> lines) {

    public PlaceOrderCommand {
        lines = List.copyOf(lines);
    }
}
