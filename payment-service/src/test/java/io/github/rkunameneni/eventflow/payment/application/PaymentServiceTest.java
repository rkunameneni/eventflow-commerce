package io.github.rkunameneni.eventflow.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rkunameneni.eventflow.events.InventoryReserved;
import io.github.rkunameneni.eventflow.events.PaymentCompleted;
import io.github.rkunameneni.eventflow.events.PaymentFailed;
import io.github.rkunameneni.eventflow.payment.domain.Payment;
import io.github.rkunameneni.eventflow.payment.domain.PaymentRepository;
import io.github.rkunameneni.eventflow.payment.provider.ChargeRequest;
import io.github.rkunameneni.eventflow.payment.provider.ChargeResult;
import io.github.rkunameneni.eventflow.payment.provider.PaymentProviderUnavailableException;
import io.github.rkunameneni.eventflow.payment.provider.ResilientPaymentGateway;
import io.github.rkunameneni.eventflow.support.messaging.OutboxWriter;
import io.github.rkunameneni.eventflow.support.messaging.ProcessedEventStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2024-06-01T10:00:00Z");

    @Mock
    private PaymentRepository payments;
    @Mock
    private ProcessedEventStore processedEvents;
    @Mock
    private OutboxWriter outbox;
    @Mock
    private ResilientPaymentGateway gateway;
    @Mock
    private PlatformTransactionManager transactionManager;

    private PaymentService service;
    private InventoryReserved event;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, processedEvents, outbox, gateway,
                new TransactionTemplate(transactionManager), Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry());
        event = new InventoryReserved(UUID.randomUUID(), UUID.randomUUID(), "alice", new BigDecimal("42.00"), "USD", NOW);
    }

    @Test
    void approvedChargeCompletesThePaymentAndPublishesPaymentCompleted() {
        Payment payment = givenNewPayment();
        when(gateway.charge(any())).thenReturn(new ChargeResult.Approved("ch_1"));
        when(processedEvents.markProcessed(event.eventId(), PaymentService.CONSUMER)).thenReturn(true);

        service.handle(event);

        assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
        assertThat(payment.getProviderReference()).isEqualTo("ch_1");
        ArgumentCaptor<PaymentCompleted> published = ArgumentCaptor.forClass(PaymentCompleted.class);
        verify(outbox).append(published.capture());
        assertThat(published.getValue().paymentId()).isEqualTo(payment.getId());
    }

    @Test
    void theIdempotencyKeySentToTheProviderIsThePaymentId() {
        Payment payment = givenNewPayment();
        when(gateway.charge(any())).thenReturn(new ChargeResult.Approved("ch_1"));
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);

        service.handle(event);

        ArgumentCaptor<ChargeRequest> request = ArgumentCaptor.forClass(ChargeRequest.class);
        verify(gateway).charge(request.capture());
        assertThat(request.getValue().idempotencyKey()).isEqualTo(payment.getId().toString());
        assertThat(request.getValue().amount()).isEqualByComparingTo("42.00");
    }

    @Test
    void declinedChargeFailsThePaymentAndPublishesPaymentFailed() {
        Payment payment = givenNewPayment();
        when(gateway.charge(any())).thenReturn(new ChargeResult.Declined("insufficient_funds"));
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);

        service.handle(event);

        assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
        ArgumentCaptor<PaymentFailed> published = ArgumentCaptor.forClass(PaymentFailed.class);
        verify(outbox).append(published.capture());
        assertThat(published.getValue().reason()).isEqualTo("PAYMENT_DECLINED: insufficient_funds");
    }

    @Test
    void providerOutagesPropagateSoTheEventIsRetriedAndThePaymentStaysPending() {
        Payment payment = givenNewPayment();
        when(gateway.charge(any())).thenThrow(new PaymentProviderUnavailableException("down", null));

        assertThatThrownBy(() -> service.handle(event)).isInstanceOf(PaymentProviderUnavailableException.class);

        assertThat(payment.getStatus()).isEqualTo(Payment.Status.PENDING);
        verify(processedEvents, never()).markProcessed(any(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void alreadyProcessedEventsAreSkipped() {
        when(processedEvents.isProcessed(event.eventId(), PaymentService.CONSUMER)).thenReturn(true);

        service.handle(event);

        verifyNoInteractions(payments, gateway, outbox);
    }

    private Payment givenNewPayment() {
        Payment payment = Payment.start(event.orderId(), "alice", event.totalAmount(), "USD", "mockpay", NOW);
        when(gateway.providerName()).thenReturn("mockpay");
        when(payments.findByOrderId(event.orderId())).thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenReturn(payment);
        lenient().when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        return payment;
    }
}
