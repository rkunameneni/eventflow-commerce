package io.github.rkunameneni.eventflow.support.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * HS256 token handling. A shared symmetric key keeps the local stack self-contained; in AWS the key
 * comes from Secrets Manager, and moving to an external IdP only means swapping the decoder for
 * {@code NimbusJwtDecoder.withJwkSetUri(...)} - authorization rules stay unchanged.
 */
public final class JwtSupport {

    public static final String ROLES_CLAIM = "roles";
    public static final String ISSUER = "eventflow";

    private static final int MIN_SECRET_BYTES = 32;

    private JwtSupport() {
    }

    public static JwtDecoder decoder(String secret) {
        return NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Maps the {@code roles} claim to {@code ROLE_*} authorities and uses {@code sub} as the principal name. */
    public static JwtAuthenticationConverter authenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** Issues a signed access token. Used by tests and tooling; production tokens come from the gateway or an IdP. */
    public static String issue(String secret, String subject, Collection<String> roles, Duration ttl) {
        return issue(secret, subject, roles, Instant.now(), ttl);
    }

    public static String issue(String secret, String subject, Collection<String> roles, Instant issuedAt,
            Duration ttl) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .claim(ROLES_CLAIM, List.copyOf(roles))
                .build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key(secret)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static SecretKey key(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "eventflow.security.jwt-secret must be configured with at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
