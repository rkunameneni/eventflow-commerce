package io.github.rkunameneni.eventflow.gateway.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stand-in for an identity provider so the stack can be tried with curl. Enabled only when
 * {@code eventflow.security.demo-issuer.enabled=true} (docker-compose sets it; the Helm chart and
 * Terraform do not). In production, tokens come from an OIDC provider such as Cognito.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "eventflow.security.demo-issuer.enabled", havingValue = "true")
public class DemoTokenController {

    private final GatewaySecurityProperties.DemoIssuer settings;
    private final TokenService tokens;

    public DemoTokenController(GatewaySecurityProperties properties, TokenService tokens) {
        this.settings = properties.demoIssuer();
        this.tokens = tokens;
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        GatewaySecurityProperties.DemoUser user = settings.users().stream()
                .filter(candidate -> candidate.username().equals(request.username()))
                .filter(candidate -> constantTimeEquals(candidate.password(), request.password()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        String token = tokens.issue(user.username(), user.roles(), settings.tokenTtl());
        return new TokenResponse(token, "Bearer", settings.tokenTtl().toSeconds());
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public record TokenRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
