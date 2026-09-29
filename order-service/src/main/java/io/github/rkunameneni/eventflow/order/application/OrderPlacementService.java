package io.github.rkunameneni.eventflow.order.application;

import io.github.rkunameneni.eventflow.events.OrderCreated;
import io.github.rkunameneni.eventflow.events.OrderLine;
import io.github.rkunameneni.eventflow.order.domain.IdempotencyRecord;
import io.github.rkunameneni.eventflow.order.domain.IdempotencyRecordRepository;
import io.github.rkunameneni.eventflow.order.domain.Order;
import io.github.rkunameneni.eventflow.order.domain.OrderItem;
import io.github.rkunameneni.eventflow.order.domain.OrderRepository;
import io.github.rkunameneni.eventflow.support.messaging.OutboxWriter;
import io.github.rkunameneni.eventflow.support.web.error.BusinessRuleViolationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts new orders. The order row, the idempotency record and the {@code OrderCreated} outbox
 * row are written in one local transaction - this is what makes the saga start reliably without
 * a distributed transaction between Postgres and Kafka.
 */
@Service
public class OrderPlacementService {

    private final OrderRepository orders;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final Counter placedOrders;
    private final Counter replayedRequests;

    public OrderPlacementService(OrderRepository orders, IdempotencyRecordRepository idempotencyRecords,
            OutboxWriter outbox, Clock clock, MeterRegistry meterRegistry) {
        this.orders = orders;
        this.idempotencyRecords = idempotencyRecords;
        this.outbox = outbox;
        this.clock = clock;
        this.placedOrders = Counter.builder("eventflow.orders.placed")
                .description("Orders accepted").register(meterRegistry);
        this.replayedRequests = Counter.builder("eventflow.orders.idempotent.replays")
                .description("POST /orders retries answered from the idempotency store").register(meterRegistry);
    }

    @Transactional
    public PlacementResult place(PlaceOrderCommand command) {
        String fingerprint = RequestFingerprint.of(command.currency(), command.lines());
        var key = new IdempotencyRecord.Key(command.customerId(), command.idempotencyKey());

        Optional<IdempotencyRecord> previous = idempotencyRecords.findById(key);
        if (previous.isPresent()) {
            return replay(previous.get(), fingerprint);
        }

        rejectDuplicateSkus(command.lines());
        Instant now = clock.instant();
        Order order = Order.place(command.customerId(), command.currency(), command.lines(), now);
        orders.save(order);
        // Flushed eagerly: a concurrent request with the same key fails here, before any event is written.
        idempotencyRecords.saveAndFlush(new IdempotencyRecord(key, fingerprint, order.getId(), now));
        outbox.append(orderCreated(order, now));

        placedOrders.increment();
        return PlacementResult.created(order);
    }

    private PlacementResult replay(IdempotencyRecord previous, String fingerprint) {
        if (!previous.matches(fingerprint)) {
            throw new BusinessRuleViolationException("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request.");
        }
        Order original = orders.findWithItemsById(previous.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Idempotency record points to a missing order"));
        replayedRequests.increment();
        return PlacementResult.replayed(original);
    }

    private static void rejectDuplicateSkus(List<Order.LineItem> lines) {
        Set<String> skus = new HashSet<>();
        for (Order.LineItem line : lines) {
            if (!skus.add(line.sku())) {
                throw new BusinessRuleViolationException("DUPLICATE_SKU",
                        "SKU " + line.sku() + " appears on more than one line; combine the quantities.");
            }
        }
    }

    private static OrderCreated orderCreated(Order order, Instant now) {
        List<OrderLine> lines = order.getItems().stream().map(OrderItem::toEventLine).toList();
        return new OrderCreated(UUID.randomUUID(), order.getId(), order.getCustomerId(), lines,
                order.getTotalAmount(), order.getCurrency(), now);
    }
}
