package io.github.rkunameneni.eventflow.payment.provider;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Adapter for "MockPay", an external HTTP payment API (served by WireMock in local runs and
 * tests). Maps HTTP semantics onto the {@link PaymentProvider} contract: 5xx and I/O errors are
 * retryable, a 4xx means the provider refused this exact request and retrying cannot help.
 */
@Component
public class MockPayProvider implements PaymentProvider {

    public static final String NAME = "mockpay";

    private final RestClient restClient;

    public MockPayProvider(RestClient.Builder builder, PaymentProperties properties) {
        PaymentProperties.MockPay settings = properties.mockpay();
        // HTTP/1.1 explicitly: the JDK client's default h2c upgrade attempt is not supported by
        // every provider endpoint or proxy.
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(settings.connectTimeout())
                .build());
        requestFactory.setReadTimeout(settings.readTimeout());
        this.restClient = builder
                .baseUrl(settings.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        try {
            ChargeResponse response = restClient.post()
                    .uri("/v1/charges")
                    .header("Idempotency-Key", request.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChargeBody(request.orderId().toString(), request.customerId(), request.amount(),
                            request.currency()))
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new PaymentProviderException("MockPay answered " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new RequestRefusedException(res.getStatusCode().value());
                    })
                    .body(ChargeResponse.class);
            return toResult(response);
        } catch (RequestRefusedException e) {
            return new ChargeResult.Declined("PROVIDER_REFUSED_REQUEST: HTTP " + e.status);
        } catch (ResourceAccessException e) {
            throw new PaymentProviderException("MockPay is unreachable: " + e.getMessage(), e);
        }
    }

    private static ChargeResult toResult(ChargeResponse response) {
        if (response == null || response.status() == null) {
            throw new PaymentProviderException("MockPay returned an empty response");
        }
        return switch (response.status()) {
            case "APPROVED" -> new ChargeResult.Approved(response.id());
            case "DECLINED" -> new ChargeResult.Declined(
                    response.declineReason() == null ? "declined" : response.declineReason());
            default -> throw new PaymentProviderException("Unknown MockPay status " + response.status());
        };
    }

    record ChargeBody(String reference, String customer, BigDecimal amount, String currency) {
    }

    record ChargeResponse(String id, String status, String declineReason) {
    }

    private static final class RequestRefusedException extends RuntimeException {

        private final int status;

        RequestRefusedException(int status) {
            super("HTTP " + status, null, false, false);
            this.status = status;
        }
    }
}
