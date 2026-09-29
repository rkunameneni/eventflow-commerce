package io.github.rkunameneni.eventflow.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate. State transitions are guarded here rather than in services so that no caller
 * can move an order backwards (e.g. re-open a cancelled order after a late event).
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Order() {
    }

    public static Order place(String customerId, String currency, List<LineItem> lines, Instant now) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line");
        }
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.currency = currency;
        order.status = OrderStatus.PENDING;
        order.createdAt = now;
        order.updatedAt = now;
        int lineNumber = 1;
        for (LineItem line : lines) {
            order.items.add(new OrderItem(order, lineNumber++, line.sku(), line.quantity(), line.unitPrice()));
        }
        order.totalAmount = order.items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return order;
    }

    /** @return {@code true} if the transition happened; late or duplicate signals are ignored. */
    public boolean markInventoryReserved(Instant now) {
        if (status != OrderStatus.PENDING) {
            return false;
        }
        return transition(OrderStatus.INVENTORY_RESERVED, null, now);
    }

    /**
     * Payment can complete before this service has seen {@code InventoryReserved} (the events
     * travel on different topics), so confirmation is accepted from any non-terminal state.
     */
    public boolean confirm(Instant now) {
        if (status.isTerminal()) {
            return false;
        }
        return transition(OrderStatus.CONFIRMED, null, now);
    }

    public boolean cancel(String reason, Instant now) {
        if (status.isTerminal()) {
            return false;
        }
        return transition(OrderStatus.CANCELLED, reason, now);
    }

    private boolean transition(OrderStatus target, String reason, Instant now) {
        this.status = target;
        this.statusReason = reason;
        this.updatedAt = now;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** A requested order line, validated by the API layer before it reaches the aggregate. */
    public record LineItem(String sku, int quantity, BigDecimal unitPrice) {
    }
}
