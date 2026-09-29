package io.github.rkunameneni.eventflow.payment.web;

import io.github.rkunameneni.eventflow.payment.application.PaymentQueryService;
import io.github.rkunameneni.eventflow.payment.domain.Payment;
import io.github.rkunameneni.eventflow.support.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentQueryService queries;

    public PaymentController(PaymentQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get the payment of an order")
    public PaymentResponse getPayment(@PathVariable UUID orderId, Authentication authentication) {
        return PaymentResponse.from(queries.forOrder(orderId, CurrentUser.from(authentication)));
    }

    public record PaymentResponse(UUID id, UUID orderId, String customerId, BigDecimal amount, String currency,
                                  Payment.Status status, String provider, String providerReference,
                                  String failureReason, Instant createdAt, Instant updatedAt) {

        static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getCustomerId(),
                    payment.getAmount(), payment.getCurrency(), payment.getStatus(), payment.getProvider(),
                    payment.getProviderReference(), payment.getFailureReason(), payment.getCreatedAt(),
                    payment.getUpdatedAt());
        }
    }
}
