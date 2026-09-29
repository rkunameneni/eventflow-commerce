package io.github.rkunameneni.eventflow.events;

import java.util.List;
import java.util.Map;

/**
 * Kafka topic names. One topic per event type keeps consumers simple (a topic carries exactly
 * one payload schema) and lets each type be retained, partitioned and secured independently.
 * The {@code .v1} suffix leaves room to roll out a breaking schema change side by side.
 */
public final class Topics {

    public static final String ORDER_CREATED = "eventflow.order.created.v1";
    public static final String INVENTORY_RESERVED = "eventflow.inventory.reserved.v1";
    public static final String INVENTORY_REJECTED = "eventflow.inventory.rejected.v1";
    public static final String PAYMENT_COMPLETED = "eventflow.payment.completed.v1";
    public static final String PAYMENT_FAILED = "eventflow.payment.failed.v1";
    public static final String ORDER_CONFIRMED = "eventflow.order.confirmed.v1";
    public static final String ORDER_CANCELLED = "eventflow.order.cancelled.v1";

    /** Suffix of the dead-letter topic that pairs with every saga topic. */
    public static final String DLT_SUFFIX = ".DLT";

    public static final List<String> ALL = List.of(
            ORDER_CREATED, INVENTORY_RESERVED, INVENTORY_REJECTED,
            PAYMENT_COMPLETED, PAYMENT_FAILED, ORDER_CONFIRMED, ORDER_CANCELLED);

    private static final Map<Class<? extends SagaEvent>, String> BY_TYPE = Map.of(
            OrderCreated.class, ORDER_CREATED,
            InventoryReserved.class, INVENTORY_RESERVED,
            InventoryRejected.class, INVENTORY_REJECTED,
            PaymentCompleted.class, PAYMENT_COMPLETED,
            PaymentFailed.class, PAYMENT_FAILED,
            OrderConfirmed.class, ORDER_CONFIRMED,
            OrderCancelled.class, ORDER_CANCELLED);

    private Topics() {
    }

    public static String topicFor(SagaEvent event) {
        return topicFor(event.getClass());
    }

    public static String topicFor(Class<? extends SagaEvent> eventType) {
        return BY_TYPE.get(eventType);
    }

    public static String deadLetterTopic(String topic) {
        return topic + DLT_SUFFIX;
    }
}
