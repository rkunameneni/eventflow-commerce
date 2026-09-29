package io.github.rkunameneni.eventflow.payment.provider;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param provider name of the active {@link PaymentProvider} strategy
 */
@ConfigurationProperties("eventflow.payment")
public record PaymentProperties(
        @DefaultValue("mockpay") String provider,
        @DefaultValue MockPay mockpay,
        @DefaultValue Sandbox sandbox) {

    /** HTTP settings for the external mock provider (WireMock locally). */
    public record MockPay(
            @DefaultValue("http://localhost:9090") URI baseUrl,
            @DefaultValue("1s") Duration connectTimeout,
            @DefaultValue("2s") Duration readTimeout) {
    }

    /** In-process provider for demos and load tests: approves charges up to a limit. */
    public record Sandbox(@DefaultValue("1000.00") BigDecimal approvalLimit) {
    }
}
