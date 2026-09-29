package io.github.rkunameneni.eventflow.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.notification.domain.Channel;
import io.github.rkunameneni.eventflow.notification.domain.Notification;
import io.github.rkunameneni.eventflow.notification.domain.NotificationRepository;
import io.github.rkunameneni.eventflow.support.messaging.ProcessedEventStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2024-06-01T10:00:00Z");

    @Mock
    private NotificationRepository notifications;
    @Mock
    private ProcessedEventStore processedEvents;
    @Mock
    private NotificationSender emailSender;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        when(emailSender.channel()).thenReturn(Channel.EMAIL);
        service = new NotificationService(notifications, processedEvents, new MessageTemplates(),
                List.of(emailSender), Channel.EMAIL, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sendsAndRecordsAConfirmation() {
        when(processedEvents.markProcessed(any(), any())).thenReturn(true);
        var event = new OrderConfirmed(UUID.randomUUID(), UUID.randomUUID(), "alice", BigDecimal.TEN, "USD", NOW);

        service.onConfirmed(event);

        verify(emailSender).send(any(), any());
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(Notification.Type.ORDER_CONFIRMED);
        assertThat(saved.getValue().getCustomerId()).isEqualTo("alice");
        assertThat(saved.getValue().getSentAt()).isEqualTo(NOW);
    }

    @Test
    void duplicateEventsDoNotNotifyTwice() {
        when(processedEvents.markProcessed(any(), any())).thenReturn(false);

        service.onCancelled(new OrderCancelled(UUID.randomUUID(), UUID.randomUUID(), "alice", "PAYMENT_DECLINED", NOW));

        verify(emailSender, never()).send(any(), any());
        verify(notifications, never()).save(any());
    }

    @Test
    void refusesToStartWithoutASenderForTheDefaultChannel() {
        assertThatThrownBy(() -> new NotificationService(notifications, processedEvents, new MessageTemplates(),
                List.of(emailSender), Channel.SMS, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS");
    }
}
