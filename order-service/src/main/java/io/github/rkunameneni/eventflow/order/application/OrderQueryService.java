package io.github.rkunameneni.eventflow.order.application;

import io.github.rkunameneni.eventflow.order.domain.Order;
import io.github.rkunameneni.eventflow.order.domain.OrderRepository;
import io.github.rkunameneni.eventflow.order.domain.OrderStatus;
import io.github.rkunameneni.eventflow.support.security.CurrentUser;
import io.github.rkunameneni.eventflow.support.web.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orders;

    public OrderQueryService(OrderRepository orders) {
        this.orders = orders;
    }

    /** Another customer's order is reported as not found rather than forbidden, to avoid leaking ids. */
    public Order get(UUID orderId, CurrentUser user) {
        return orders.findWithItemsById(orderId)
                .filter(order -> user.canAccess(order.getCustomerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    /** Customers always see only their own orders; admins may filter by customer. */
    public Page<Order> search(CurrentUser user, String customerFilter, OrderStatus status, Pageable pageable) {
        String customerId = user.admin() ? customerFilter : user.id();
        if (customerId != null && status != null) {
            return orders.findByCustomerIdAndStatus(customerId, status, pageable);
        }
        if (customerId != null) {
            return orders.findByCustomerId(customerId, pageable);
        }
        if (status != null) {
            return orders.findByStatus(status, pageable);
        }
        return orders.findAll(pageable);
    }
}
