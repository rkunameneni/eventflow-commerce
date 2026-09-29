package io.github.rkunameneni.eventflow.payment.messaging;

import io.github.rkunameneni.eventflow.events.EventJson;
import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.payment.application.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class PaymentSagaListener {

    private final PaymentService payments;

    PaymentSagaListener(PaymentService payments) {
        this.payments = payments;
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVED)
    void onInventoryReserved(String payload) {
        payments.handle(EventJson.read(payload, InventoryReserved.class));
    }
}
