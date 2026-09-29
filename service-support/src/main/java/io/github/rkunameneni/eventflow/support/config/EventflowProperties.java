package io.github.rkunameneni.eventflow.support.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Platform settings shared by every service, bound from the {@code eventflow.*} namespace. */
@ConfigurationProperties("eventflow")
public record EventflowProperties(
        @DefaultValue Security security,
        @DefaultValue Outbox outbox,
        @DefaultValue ConsumerRetry consumerRetry,
        @DefaultValue TopicDefaults topics) {

    /**
     * @param jwtSecret      HMAC key (at least 256 bits) used to verify HS256 access tokens
     * @param publicGetPaths extra GET endpoints reachable without a token, e.g. a public catalogue
     */
    public record Security(String jwtSecret, @DefaultValue List<String> publicGetPaths) {
    }

    /**
     * @param batchSize   rows claimed per relay iteration
     * @param sendTimeout how long the relay waits for a broker acknowledgement
     * @param retention   how long published rows are kept for troubleshooting before purge
     */
    public record Outbox(
            @DefaultValue("100") int batchSize,
            @DefaultValue("10s") Duration sendTimeout,
            @DefaultValue("7d") Duration retention) {
    }

    /**
     * Blocking retries for a failed record before it is routed to its dead-letter topic.
     *
     * @param maxRetries retries after the first attempt
     */
    public record ConsumerRetry(
            @DefaultValue("4") int maxRetries,
            @DefaultValue("500ms") Duration initialInterval,
            @DefaultValue("2.0") double multiplier,
            @DefaultValue("10s") Duration maxInterval) {
    }

    public record TopicDefaults(
            @DefaultValue("3") int partitions,
            @DefaultValue("1") short replicationFactor) {
    }
}
