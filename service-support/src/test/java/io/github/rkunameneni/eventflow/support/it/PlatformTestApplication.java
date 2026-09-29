package io.github.rkunameneni.eventflow.support.it;

import io.github.rkunameneni.eventflow.events.EventJson;
import io.github.rkunameneni.eventflow.events.PaymentFailed;
import io.github.rkunameneni.eventflow.events.SagaEvent;
import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.support.messaging.OutboxWriter;
import io.github.rkunameneni.eventflow.support.messaging.ProcessedEventStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Minimal service used to exercise the platform starter against real Postgres and Kafka. */
@SpringBootApplication
public class PlatformTestApplication {

    static final String POISON_CUSTOMER = "poison";

    @Component
    static class ProbeListener {

        final Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final ProbeHandler handler;

        ProbeListener(ProbeHandler handler) {
            this.handler = handler;
        }

        @KafkaListener(topics = Topics.PAYMENT_FAILED)
        void onPaymentFailed(String payload) {
            PaymentFailed event = EventJson.read(payload, PaymentFailed.class);
            attempts.computeIfAbsent(event.eventId(), id -> new AtomicInteger()).incrementAndGet();
            handler.handle(event);
        }
    }

    @Component
    static class ProbeHandler {

        private final List<UUID> handled = new CopyOnWriteArrayList<>();
        private volatile boolean poisonFails = true;

        private final ProcessedEventStore processedEvents;
        private final OutboxWriter outbox;

        ProbeHandler(ProcessedEventStore processedEvents, OutboxWriter outbox) {
            this.processedEvents = processedEvents;
            this.outbox = outbox;
        }

        @Transactional
        public void handle(PaymentFailed event) {
            if (!processedEvents.markProcessed(event.eventId(), "probe")) {
                return;
            }
            if (poisonFails && POISON_CUSTOMER.equals(event.customerId())) {
                throw new IllegalStateException("Simulated downstream outage");
            }
            handled.add(event.eventId());
        }

        @Transactional
        public void emit(SagaEvent event) {
            outbox.append(event);
        }

        // Accessed through the transactional proxy, so state is exposed via methods, not fields.
        public List<UUID> handled() {
            return handled;
        }

        public void recoverFromOutage() {
            poisonFails = false;
        }
    }
}
