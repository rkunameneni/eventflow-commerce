package io.github.rkunameneni.eventflow.inventory.application;

import io.github.rkunameneni.eventflow.events.InventoryRejected;
import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.events.OrderCreated;
import io.github.rkunameneni.eventflow.events.OrderLine;
import io.github.rkunameneni.eventflow.inventory.domain.Product;
import io.github.rkunameneni.eventflow.inventory.domain.ProductRepository;
import io.github.rkunameneni.eventflow.inventory.domain.Reservation;
import io.github.rkunameneni.eventflow.inventory.domain.ReservationRepository;
import io.github.rkunameneni.eventflow.support.messaging.OutboxWriter;
import io.github.rkunameneni.eventflow.support.messaging.ProcessedEventStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventory's part of the saga: reserve stock for new orders (all lines or nothing), release it
 * when an order is cancelled and commit it when an order is confirmed.
 */
@Service
public class ReservationService {

    static final String CONSUMER = "inventory-service";

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final ProcessedEventStore processedEvents;
    private final OutboxWriter outbox;
    private final CatalogCache catalogCache;
    private final Clock clock;

    public ReservationService(ProductRepository products, ReservationRepository reservations,
            ProcessedEventStore processedEvents, OutboxWriter outbox, CatalogCache catalogCache, Clock clock) {
        this.products = products;
        this.reservations = reservations;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.catalogCache = catalogCache;
        this.clock = clock;
    }

    @Transactional
    public void reserve(OrderCreated order) {
        if (isDuplicate(order.eventId())) {
            return;
        }
        List<String> skus = order.lines().stream().map(OrderLine::sku).distinct().toList();
        Map<String, Product> locked = products.lockAllBySkuIn(skus).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));
        Instant now = clock.instant();

        Optional<String> rejection = StockCheck.rejectionReason(order, locked);
        if (rejection.isPresent()) {
            log.info("Rejecting order {}: {}", order.orderId(), rejection.get());
            outbox.append(new InventoryRejected(UUID.randomUUID(), order.orderId(), order.customerId(),
                    rejection.get(), now));
            return;
        }

        for (OrderLine line : order.lines()) {
            locked.get(line.sku()).reserve(line.quantity(), now);
            reservations.save(new Reservation(order.orderId(), line.sku(), line.quantity(), now));
        }
        catalogCache.evictAvailability(skus);
        outbox.append(new InventoryReserved(UUID.randomUUID(), order.orderId(), order.customerId(),
                order.totalAmount(), order.currency(), now));
        log.info("Reserved {} line(s) for order {}", order.lines().size(), order.orderId());
    }

    /** Compensating action: a cancelled order gives its units back. */
    @Transactional
    public void release(OrderCancelled cancelled) {
        settle(cancelled.eventId(), cancelled.orderId(), (product, reservation, now) -> {
            product.release(reservation.getQuantity(), now);
            reservation.release(now);
        });
    }

    @Transactional
    public void commit(OrderConfirmed confirmed) {
        settle(confirmed.eventId(), confirmed.orderId(), (product, reservation, now) -> {
            product.commit(reservation.getQuantity(), now);
            reservation.commit(now);
        });
    }

    private void settle(UUID eventId, UUID orderId, Settlement settlement) {
        if (isDuplicate(eventId)) {
            return;
        }
        List<Reservation> held = reservations.findByOrderIdAndStatus(orderId, Reservation.Status.RESERVED);
        if (held.isEmpty()) {
            // Nothing was reserved (e.g. inventory itself rejected the order).
            return;
        }
        List<String> skus = held.stream().map(Reservation::getSku).distinct().toList();
        Map<String, Product> locked = products.lockAllBySkuIn(skus).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));
        Instant now = clock.instant();
        held.forEach(reservation -> settlement.apply(locked.get(reservation.getSku()), reservation, now));
        catalogCache.evictAvailability(skus);
    }

    private boolean isDuplicate(UUID eventId) {
        boolean duplicate = !processedEvents.markProcessed(eventId, CONSUMER);
        if (duplicate) {
            log.info("Skipping duplicate event {}", eventId);
        }
        return duplicate;
    }

    @FunctionalInterface
    private interface Settlement {
        void apply(Product product, Reservation reservation, Instant now);
    }
}
