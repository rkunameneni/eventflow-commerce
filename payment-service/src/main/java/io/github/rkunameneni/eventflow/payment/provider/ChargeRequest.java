package io.github.rkunameneni.eventflow.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param idempotencyKey stable per payment, so provider-side de-duplication makes retries safe
 */
public record ChargeRequest(String idempotencyKey, UUID orderId, String customerId, BigDecimal amount,
                            String currency) {
}
