package io.github.rkunameneni.eventflow.notification.messaging;

import io.github.rkunameneni.eventflow.events.EventJson;
import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.notification.application.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class NotificationListener {

    private final NotificationService notifications;

    NotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(topics = Topics.ORDER_CONFIRMED)
    void onOrderConfirmed(String payload) {
        notifications.onConfirmed(EventJson.read(payload, OrderConfirmed.class));
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    void onOrderCancelled(String payload) {
        notifications.onCancelled(EventJson.read(payload, OrderCancelled.class));
    }
}
