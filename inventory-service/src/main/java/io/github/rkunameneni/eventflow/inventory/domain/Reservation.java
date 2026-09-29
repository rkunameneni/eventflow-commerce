package io.github.rkunameneni.eventflow.inventory.domain;

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

/** Units of one SKU held for one order. */
@Entity
@Table(name = "reservations")
public class Reservation {

    public enum Status { RESERVED, RELEASED, COMMITTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    public Reservation(UUID orderId, String sku, int quantity, Instant now) {
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.status = Status.RESERVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void release(Instant now) {
        transition(Status.RELEASED, now);
    }

    public void commit(Instant now) {
        transition(Status.COMMITTED, now);
    }

    private void transition(Status target, Instant now) {
        if (status != Status.RESERVED) {
            throw new IllegalStateException("Reservation " + id + " is already " + status);
        }
        status = target;
        updatedAt = now;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public Status getStatus() {
        return status;
    }
}
