package io.github.rkunameneni.eventflow.payment.application;

import io.github.rkunameneni.eventflow.payment.domain.Payment;
import io.github.rkunameneni.eventflow.payment.domain.PaymentRepository;
import io.github.rkunameneni.eventflow.support.security.CurrentUser;
import io.github.rkunameneni.eventflow.support.web.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentQueryService {

    private final PaymentRepository payments;

    public PaymentQueryService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public Payment forOrder(UUID orderId, CurrentUser user) {
        return payments.findByOrderId(orderId)
                .filter(payment -> user.canAccess(payment.getCustomerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", orderId));
    }
}
