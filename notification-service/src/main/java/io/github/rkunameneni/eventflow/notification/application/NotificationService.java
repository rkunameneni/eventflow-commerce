package io.github.rkunameneni.eventflow.notification.application;

import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.events.SagaEvent;
import io.github.rkunameneni.eventflow.notification.domain.Channel;
import io.github.rkunameneni.eventflow.notification.domain.Message;
import io.github.rkunameneni.eventflow.notification.domain.Notification;
import io.github.rkunameneni.eventflow.notification.domain.NotificationRepository;
import io.github.rkunameneni.eventflow.support.messaging.ProcessedEventStore;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells customers how their order ended. Delivery is at-least-once: if the transaction fails
 * after the message went out, the redelivered event sends it again, which is the right trade-off
 * for a notification (a duplicate e-mail beats a missing one).
 */
@Service
public class NotificationService {

    static final String CONSUMER = "notification-service";

    private final NotificationRepository notifications;
    private final ProcessedEventStore processedEvents;
    private final MessageTemplates templates;
    private final Map<Channel, NotificationSender> senders;
    private final Channel defaultChannel;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, ProcessedEventStore processedEvents,
            MessageTemplates templates, List<NotificationSender> senders,
            @Value("${eventflow.notifications.default-channel:EMAIL}") Channel defaultChannel, Clock clock) {
        this.notifications = notifications;
        this.processedEvents = processedEvents;
        this.templates = templates;
        this.senders = senders.stream().collect(Collectors.toMap(NotificationSender::channel, Function.identity()));
        this.defaultChannel = defaultChannel;
        this.clock = clock;
        if (!this.senders.containsKey(defaultChannel)) {
            throw new IllegalStateException("No sender registered for channel " + defaultChannel);
        }
    }

    @Transactional
    public void onConfirmed(OrderConfirmed event) {
        notify(event, Notification.Type.ORDER_CONFIRMED, templates.confirmed(event));
    }

    @Transactional
    public void onCancelled(OrderCancelled event) {
        notify(event, Notification.Type.ORDER_CANCELLED, templates.cancelled(event));
    }

    private void notify(SagaEvent event, Notification.Type type, Message message) {
        if (!processedEvents.markProcessed(event.eventId(), CONSUMER)) {
            return;
        }
        senders.get(defaultChannel).send(event.customerId(), message);
        notifications.save(new Notification(event.orderId(), event.customerId(), type, defaultChannel, message,
                clock.instant()));
    }
}
