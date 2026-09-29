package io.github.rkunameneni.eventflow.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkunameneni.eventflow.order.domain.Order;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    @Test
    void equivalentPricesProduceTheSameFingerprint() {
        String a = RequestFingerprint.of("USD", List.of(new Order.LineItem("SKU-1", 1, new BigDecimal("10.0"))));
        String b = RequestFingerprint.of("USD", List.of(new Order.LineItem("SKU-1", 1, new BigDecimal("10.00"))));

        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void anyMaterialChangeProducesADifferentFingerprint() {
        String base = RequestFingerprint.of("USD", List.of(new Order.LineItem("SKU-1", 1, BigDecimal.TEN)));

        assertThat(RequestFingerprint.of("EUR", List.of(new Order.LineItem("SKU-1", 1, BigDecimal.TEN))))
                .isNotEqualTo(base);
        assertThat(RequestFingerprint.of("USD", List.of(new Order.LineItem("SKU-1", 2, BigDecimal.TEN))))
                .isNotEqualTo(base);
        assertThat(RequestFingerprint.of("USD", List.of(new Order.LineItem("SKU-9", 1, BigDecimal.TEN))))
                .isNotEqualTo(base);
    }
}
