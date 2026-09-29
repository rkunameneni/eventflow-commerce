package io.github.rkunameneni.eventflow.notification.application;

import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.notification.domain.Message;
import org.springframework.stereotype.Component;

/** Renders customer-facing text. Internal reason codes are translated, never shown verbatim. */
@Component
public class MessageTemplates {

    public Message confirmed(OrderConfirmed event) {
        return new Message(
                "Your order " + shortId(event.orderId().toString()) + " is confirmed",
                "Thanks for your order! We received your payment of " + event.totalAmount().toPlainString() + " "
                        + event.currency() + " and your items are being prepared for shipping.");
    }

    public Message cancelled(OrderCancelled event) {
        return new Message(
                "Your order " + shortId(event.orderId().toString()) + " was cancelled",
                "We're sorry - we could not complete your order: " + explain(event.reason())
                        + " You have not been charged.");
    }

    static String explain(String reasonCode) {
        String code = reasonCode == null ? "" : reasonCode.split(":", 2)[0];
        return switch (code) {
            case "INSUFFICIENT_STOCK" -> "one of the items is out of stock.";
            case "PRICE_CHANGED" -> "the price of an item changed while you were checking out.";
            case "UNKNOWN_SKU" -> "one of the items is no longer available.";
            case "PAYMENT_DECLINED" -> "your payment was declined.";
            default -> "an unexpected problem occurred.";
        };
    }

    private static String shortId(String orderId) {
        return orderId.substring(0, 8).toUpperCase();
    }
}
