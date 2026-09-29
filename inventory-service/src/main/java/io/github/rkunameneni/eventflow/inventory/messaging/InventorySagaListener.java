package io.github.rkunameneni.eventflow.inventory.messaging;

import io.github.rkunameneni.eventflow.events.EventJson;
import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.events.OrderCreated;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.inventory.application.ReservationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class InventorySagaListener {

    private final ReservationService reservations;

    InventorySagaListener(ReservationService reservations) {
        this.reservations = reservations;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    void onOrderCreated(String payload) {
        reservations.reserve(EventJson.read(payload, OrderCreated.class));
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    void onOrderCancelled(String payload) {
        reservations.release(EventJson.read(payload, OrderCancelled.class));
    }

    @KafkaListener(topics = Topics.ORDER_CONFIRMED)
    void onOrderConfirmed(String payload) {
        reservations.commit(EventJson.read(payload, OrderConfirmed.class));
    }
}
