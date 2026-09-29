package io.github.rkunameneni.eventflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Remembers which order an {@code Idempotency-Key} created, together with a fingerprint of the
 * request body, so a retried POST returns the original order instead of creating a duplicate.
 * Keys are scoped per customer: two customers can never collide or probe each other's keys.
 *
 * <p>Implements {@link Persistable} so saving always INSERTs: a concurrent request with the same
 * key then fails on the primary key instead of silently merging over the first one.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord implements Persistable<IdempotencyRecord.Key> {

    @EmbeddedId
    private Key id;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(Key id, String requestHash, UUID orderId, Instant createdAt) {
        this.id = id;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public boolean matches(String otherRequestHash) {
        return requestHash.equals(otherRequestHash);
    }

    public UUID getOrderId() {
        return orderId;
    }

    @Override
    public Key getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.isNew = false;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "customer_id")
        private String customerId;

        @Column(name = "idempotency_key")
        private String idempotencyKey;

        protected Key() {
        }

        public Key(String customerId, String idempotencyKey) {
            this.customerId = customerId;
            this.idempotencyKey = idempotencyKey;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && customerId.equals(key.customerId)
                    && idempotencyKey.equals(key.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(customerId, idempotencyKey);
        }
    }
}
