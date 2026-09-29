package io.github.rkunameneni.eventflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A catalogue entry and its stock. {@code available} is what can still be sold; {@code reserved}
 * is held for orders whose payment is in flight and is either committed or released later.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Product() {
    }

    public Product(String sku, String name, BigDecimal unitPrice, String currency, int availableQuantity,
            Instant now) {
        this.sku = sku;
        update(name, unitPrice, currency, availableQuantity, now);
    }

    public final void update(String name, BigDecimal unitPrice, String currency, int availableQuantity, Instant now) {
        if (availableQuantity < 0) {
            throw new IllegalArgumentException("Available quantity cannot be negative");
        }
        this.name = name;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.availableQuantity = availableQuantity;
        this.updatedAt = now;
    }

    public boolean canReserve(int quantity) {
        return quantity > 0 && availableQuantity >= quantity;
    }

    /** Prices are compared by value so {@code 10.0} and {@code 10.00} are equal. */
    public boolean isPricedAt(BigDecimal quotedPrice, String quotedCurrency) {
        return currency.equals(quotedCurrency) && unitPrice.compareTo(quotedPrice) == 0;
    }

    public void reserve(int quantity, Instant now) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException("Cannot reserve " + quantity + " of " + sku);
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
        updatedAt = now;
    }

    /** Compensation: puts reserved units back on sale. */
    public void release(int quantity, Instant now) {
        requireReserved(quantity);
        reservedQuantity -= quantity;
        availableQuantity += quantity;
        updatedAt = now;
    }

    /** The order was paid for: reserved units leave the warehouse. */
    public void commit(int quantity, Instant now) {
        requireReserved(quantity);
        reservedQuantity -= quantity;
        updatedAt = now;
    }

    private void requireReserved(int quantity) {
        if (quantity <= 0 || reservedQuantity < quantity) {
            throw new IllegalStateException("Only " + reservedQuantity + " of " + sku + " are reserved");
        }
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }
}
