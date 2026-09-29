package io.github.rkunameneni.eventflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A message sent to a customer, kept as an audit trail and for the "my notifications" API. */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Type { ORDER_CONFIRMED, ORDER_CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected Notification() {
    }

    public Notification(UUID orderId, String customerId, Type type, Channel channel, Message message, Instant sentAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.type = type;
        this.channel = channel;
        this.subject = message.subject();
        this.body = message.body();
        this.sentAt = sentAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Type getType() {
        return type;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
