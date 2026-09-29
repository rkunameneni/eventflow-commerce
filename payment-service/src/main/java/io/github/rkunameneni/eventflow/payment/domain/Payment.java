package io.github.rkunameneni.eventflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One charge attempt per order. The payment id doubles as the idempotency key sent to the
 * provider, so a redelivered event re-sends the same key and cannot charge the customer twice.
 */
@Entity
@Table(name = "payments")
public class Payment {

    public enum Status { PENDING, COMPLETED, FAILED }

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Payment() {
    }

    public static Payment start(UUID orderId, String customerId, BigDecimal amount, String currency, String provider,
            Instant now) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.orderId = orderId;
        payment.customerId = customerId;
        payment.amount = amount;
        payment.currency = currency;
        payment.provider = provider;
        payment.status = Status.PENDING;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    public void complete(String reference, Instant now) {
        requirePending();
        status = Status.COMPLETED;
        providerReference = reference;
        updatedAt = now;
    }

    public void fail(String reason, Instant now) {
        requirePending();
        status = Status.FAILED;
        failureReason = reason;
        updatedAt = now;
    }

    public boolean isSettled() {
        return status != Status.PENDING;
    }

    private void requirePending() {
        if (isSettled()) {
            throw new IllegalStateException("Payment " + id + " is already " + status);
        }
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

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
