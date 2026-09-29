package io.github.rkunameneni.eventflow.payment.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

/** HTTP contract of the MockPay adapter, verified against a WireMock stub (no Spring context). */
class MockPayProviderTest {

    @RegisterExtension
    static WireMockExtension mockPay = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final UUID orderId = UUID.randomUUID();
    private MockPayProvider provider;

    @BeforeEach
    void setUp() {
        var properties = new PaymentProperties("mockpay",
                new PaymentProperties.MockPay(URI.create(mockPay.baseUrl()), Duration.ofSeconds(2), Duration.ofSeconds(3)),
                new PaymentProperties.Sandbox(new BigDecimal("1000")));
        provider = new MockPayProvider(RestClient.builder(), properties);
    }

    @Test
    void approvedChargesReturnTheProviderReference() {
        mockPay.stubFor(post("/v1/charges").willReturn(okJson("{\"id\":\"ch_123\",\"status\":\"APPROVED\"}")));

        ChargeResult result = provider.charge(request());

        assertThat(result).isEqualTo(new ChargeResult.Approved("ch_123"));
        mockPay.verify(postRequestedFor(urlEqualTo("/v1/charges"))
                .withHeader("Idempotency-Key", equalTo("pay-1"))
                .withRequestBody(equalToJson("""
                        {"reference":"%s","customer":"alice","amount":42.50,"currency":"USD"}
                        """.formatted(orderId))));
    }

    @Test
    void declinedChargesCarryTheReason() {
        mockPay.stubFor(post("/v1/charges").willReturn(
                okJson("{\"id\":\"ch_124\",\"status\":\"DECLINED\",\"declineReason\":\"insufficient_funds\"}")));

        assertThat(provider.charge(request())).isEqualTo(new ChargeResult.Declined("insufficient_funds"));
    }

    @Test
    void serverErrorsAreRetryableTechnicalFailures() {
        mockPay.stubFor(post("/v1/charges").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> provider.charge(request()))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessageContaining("503");
    }

    @Test
    void clientErrorsAreFinalDeclines() {
        mockPay.stubFor(post("/v1/charges").willReturn(aResponse().withStatus(422)));

        assertThat(provider.charge(request())).isEqualTo(new ChargeResult.Declined("PROVIDER_REFUSED_REQUEST: HTTP 422"));
    }

    @Test
    void slowResponsesHitTheReadTimeout() {
        mockPay.stubFor(post("/v1/charges").willReturn(okJson("{\"id\":\"ch_1\",\"status\":\"APPROVED\"}")
                .withFixedDelay(6_000)));

        assertThatThrownBy(() -> provider.charge(request())).isInstanceOf(PaymentProviderException.class);
    }

    private ChargeRequest request() {
        return new ChargeRequest("pay-1", orderId, "alice", new BigDecimal("42.50"), "USD");
    }
}
