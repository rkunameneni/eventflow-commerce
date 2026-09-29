package io.github.rkunameneni.eventflow.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentProviderFactoryTest {

    private final PaymentProperties properties = new PaymentProperties("sandbox",
            new PaymentProperties.MockPay(URI.create("http://localhost:1"), Duration.ofSeconds(1), Duration.ofSeconds(1)),
            new PaymentProperties.Sandbox(new BigDecimal("100.00")));
    private final SandboxPaymentProvider sandbox = new SandboxPaymentProvider(properties);
    private final PaymentProviderFactory factory = new PaymentProviderFactory(List.of(sandbox));

    @Test
    void resolvesProvidersByName() {
        assertThat(factory.get("sandbox")).isSameAs(sandbox);
    }

    @Test
    void failsFastOnAnUnknownProvider() {
        assertThatThrownBy(() -> factory.get("stripe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("available: [sandbox]");
    }

    @Test
    void sandboxApprovesUpToItsLimit() {
        assertThat(sandbox.charge(request("100.00"))).isInstanceOf(ChargeResult.Approved.class);
        assertThat(sandbox.charge(request("100.01"))).isEqualTo(
                new ChargeResult.Declined("amount exceeds sandbox limit of 100.00"));
    }

    private static ChargeRequest request(String amount) {
        return new ChargeRequest("pay-1", UUID.randomUUID(), "alice", new BigDecimal(amount), "USD");
    }
}
