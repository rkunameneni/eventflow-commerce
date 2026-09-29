package io.github.rkunameneni.eventflow.support.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtSupportTest {

    private static final String SECRET = "unit-test-signing-key-0123456789abcdefghij";

    @Test
    void issuedTokensAreAcceptedAndMappedToRoleAuthorities() {
        String token = JwtSupport.issue(SECRET, "alice", List.of("CUSTOMER"), Duration.ofMinutes(5));

        Jwt jwt = JwtSupport.decoder(SECRET).decode(token);
        var authentication = JwtSupport.authenticationConverter().convert(jwt);

        assertThat(authentication.getName()).isEqualTo("alice");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtSupport.ISSUER);
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        String token = JwtSupport.issue("another-signing-key-0123456789abcdefghijkl", "mallory",
                List.of("ADMIN"), Duration.ofMinutes(5));

        assertThatThrownBy(() -> JwtSupport.decoder(SECRET).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredTokens() {
        String token = JwtSupport.issue(SECRET, "alice", List.of("CUSTOMER"),
                Instant.now().minus(Duration.ofHours(1)), Duration.ofMinutes(5));

        assertThatThrownBy(() -> JwtSupport.decoder(SECRET).decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refusesWeakSecrets() {
        assertThatThrownBy(() -> JwtSupport.decoder("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
