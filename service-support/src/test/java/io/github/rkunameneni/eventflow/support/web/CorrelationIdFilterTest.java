package io.github.rkunameneni.eventflow.support.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void propagatesACallerSuppliedIdToTheLogContextAndResponse() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "checkout-42");
        var response = new MockHttpServletResponse();
        var seenInChain = new AtomicReference<String>();

        filter.doFilter(request, response, (req, res) -> seenInChain.set(MDC.get(CorrelationId.MDC_KEY)));

        assertThat(seenInChain).hasValue("checkout-42");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("checkout-42");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).as("MDC is cleaned up").isNull();
    }

    @Test
    void replacesUnsafeIdsToPreventLogInjection() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "abc\nFAKE LOG LINE");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationId.HEADER))
                .doesNotContain("FAKE")
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void generatesAnIdWhenNoneIsSupplied() {
        assertThat(CorrelationId.sanitizeOrGenerate(null)).hasSize(36);
        assertThat(CorrelationId.sanitizeOrGenerate("x".repeat(65))).hasSize(36);
    }
}
