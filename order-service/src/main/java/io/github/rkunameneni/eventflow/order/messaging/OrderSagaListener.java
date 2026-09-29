package io.github.rkunameneni.eventflow.order.messaging;

import io.github.rkunameneni.eventflow.events.EventJson;
import io.github.rkunameneni.eventflow.events.InventoryRejected;
import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.PaymentCompleted;
import io.github.rkunameneni.eventflow.events.PaymentFailed;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.order.application.OrderSagaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka adapter: decodes saga events and hands them to the transactional handler. */
@Component
class OrderSagaListener {

    private final OrderSagaHandler handler;

    OrderSagaListener(OrderSagaHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVED)
    void onInventoryReserved(String payload) {
        handler.on(EventJson.read(payload, InventoryReserved.class));
    }

    @KafkaListener(topics = Topics.INVENTORY_REJECTED)
    void onInventoryRejected(String payload) {
        handler.on(EventJson.read(payload, InventoryRejected.class));
    }

    @KafkaListener(topics = Topics.PAYMENT_COMPLETED)
    void onPaymentCompleted(String payload) {
        handler.on(EventJson.read(payload, PaymentCompleted.class));
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    void onPaymentFailed(String payload) {
        handler.on(EventJson.read(payload, PaymentFailed.class));
    }
}
