package io.github.rkunameneni.eventflow.payment.application;

import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.PaymentCompleted;
import io.github.rkunameneni.eventflow.events.PaymentFailed;
import io.github.rkunameneni.eventflow.payment.domain.Payment;
import io.github.rkunameneni.eventflow.payment.domain.PaymentRepository;
import io.github.rkunameneni.eventflow.payment.provider.ChargeRequest;
import io.github.rkunameneni.eventflow.payment.provider.ChargeResult;
import io.github.rkunameneni.eventflow.payment.provider.ResilientPaymentGateway;
import io.github.rkunameneni.eventflow.support.messaging.OutboxWriter;
import io.github.rkunameneni.eventflow.support.messaging.ProcessedEventStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Payment's part of the saga. The provider call happens <em>between</em> two short transactions
 * rather than inside one, so a slow provider never holds a database connection:
 *
 * <ol>
 *   <li>record (or find) a PENDING payment for the order;</li>
 *   <li>charge the provider, using the payment id as idempotency key;</li>
 *   <li>record the outcome, the {@code PaymentCompleted}/{@code PaymentFailed} outbox event and the
 *       processed-event marker atomically.</li>
 * </ol>
 *
 * If the service dies after step 2, redelivery repeats step 2 with the same idempotency key and
 * the provider returns the original result instead of charging again.
 */
@Service
public class PaymentService {

    static final String CONSUMER = "payment-service";

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final ProcessedEventStore processedEvents;
    private final OutboxWriter outbox;
    private final ResilientPaymentGateway gateway;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public PaymentService(PaymentRepository payments, ProcessedEventStore processedEvents, OutboxWriter outbox,
            ResilientPaymentGateway gateway, TransactionTemplate transactions, Clock clock,
            MeterRegistry meterRegistry) {
        this.payments = payments;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.gateway = gateway;
        this.transactions = transactions;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public void handle(InventoryReserved event) {
        if (processedEvents.isProcessed(event.eventId(), CONSUMER)) {
            log.info("Skipping duplicate InventoryReserved {}", event.eventId());
            return;
        }
        Payment payment = transactions.execute(status -> payments.findByOrderId(event.orderId())
                .orElseGet(() -> payments.save(Payment.start(event.orderId(), event.customerId(),
                        event.totalAmount(), event.currency(), gateway.providerName(), clock.instant()))));

        ChargeResult result = payment.isSettled() ? null : gateway.charge(new ChargeRequest(
                payment.getId().toString(), event.orderId(), event.customerId(), event.totalAmount(), event.currency()));

        transactions.executeWithoutResult(status -> {
            if (!processedEvents.markProcessed(event.eventId(), CONSUMER) || result == null) {
                return;
            }
            Payment current = payments.findById(payment.getId()).orElseThrow();
            if (!current.isSettled()) {
                settle(current, result, clock.instant());
            }
        });
    }

    private void settle(Payment payment, ChargeResult result, Instant now) {
        switch (result) {
            case ChargeResult.Approved approved -> {
                payment.complete(approved.providerReference(), now);
                outbox.append(new PaymentCompleted(UUID.randomUUID(), payment.getOrderId(), payment.getCustomerId(),
                        payment.getId(), payment.getAmount(), payment.getCurrency(), now));
            }
            case ChargeResult.Declined declined -> {
                payment.fail(declined.reason(), now);
                outbox.append(new PaymentFailed(UUID.randomUUID(), payment.getOrderId(), payment.getCustomerId(),
                        "PAYMENT_DECLINED: " + declined.reason(), now));
            }
        }
        meterRegistry.counter("eventflow.payments", "outcome", payment.getStatus().name().toLowerCase(),
                "provider", payment.getProvider()).increment();
        log.info("Payment {} for order {} is {}", payment.getId(), payment.getOrderId(), payment.getStatus());
    }
}
