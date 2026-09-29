package io.github.rkunameneni.eventflow.support.web;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * A business-level id that follows one customer request through HTTP calls, the outbox and every
 * Kafka hop of the saga. It complements (does not replace) the W3C trace id: traces are sampled
 * and broken by the asynchronous outbox relay, the correlation id is always present.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {
    }

    /** Accepts a caller-supplied id only if it is short and log-safe; otherwise mints a new one. */
    public static String sanitizeOrGenerate(String candidate) {
        if (candidate != null && SAFE_VALUE.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }
}
