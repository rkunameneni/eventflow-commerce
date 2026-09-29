package io.github.rkunameneni.eventflow.payment.provider;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Deterministic in-process provider: approves any charge up to the configured limit. Useful for
 * running the platform without the external mock, and for load tests that should not measure a
 * third party.
 */
@Component
public class SandboxPaymentProvider implements PaymentProvider {

    public static final String NAME = "sandbox";

    private final BigDecimal approvalLimit;

    public SandboxPaymentProvider(PaymentProperties properties) {
        this.approvalLimit = properties.sandbox().approvalLimit();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        if (request.amount().compareTo(approvalLimit) > 0) {
            return new ChargeResult.Declined("amount exceeds sandbox limit of " + approvalLimit.toPlainString());
        }
        return new ChargeResult.Approved("sbx_" + request.idempotencyKey());
    }
}
