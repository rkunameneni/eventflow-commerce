package io.github.rkunameneni.eventflow.gateway.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param jwtSecret  HMAC key shared with the services (from Secrets Manager / a Kubernetes secret)
 * @param demoIssuer local token endpoint, disabled unless explicitly switched on
 */
@ConfigurationProperties("eventflow.security")
public record GatewaySecurityProperties(String jwtSecret, @DefaultValue DemoIssuer demoIssuer) {

    private static final int MIN_SECRET_BYTES = 32;

    public SecretKey signingKey() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("eventflow.security.jwt-secret must be at least 32 bytes");
        }
        return new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public record DemoIssuer(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("30m") Duration tokenTtl,
            @DefaultValue List<DemoUser> users) {
    }

    public record DemoUser(String username, String password, List<String> roles) {
    }
}
