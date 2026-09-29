package io.github.rkunameneni.eventflow.gateway.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/** Signs access tokens with the key the gateway and services use for verification. */
@Component
public class TokenService {

    public static final String ROLES_CLAIM = "roles";
    public static final String ISSUER = "eventflow";

    private final JwtEncoder encoder;
    private final Clock clock;

    @Autowired
    public TokenService(GatewaySecurityProperties properties) {
        this(new NimbusJwtEncoder(new ImmutableSecret<>(properties.signingKey())), Clock.systemUTC());
    }

    TokenService(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    public String issue(String subject, List<String> roles, Duration ttl) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(ROLES_CLAIM, List.copyOf(roles))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
