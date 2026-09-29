package io.github.rkunameneni.eventflow.support.test;

import io.github.rkunameneni.eventflow.support.security.JwtSupport;
import java.time.Duration;
import java.util.List;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Mints bearer tokens signed with the key the services under test are configured with. */
public final class TestJwt {

    public static final String SECRET = "integration-test-signing-key-0123456789abcdef";

    private TestJwt() {
    }

    public static String bearer(String subject, String... roles) {
        return "Bearer " + JwtSupport.issue(SECRET, subject, List.of(roles), Duration.ofMinutes(10));
    }

    public static String admin() {
        return bearer("ops-admin", "ADMIN");
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("eventflow.security.jwt-secret", () -> SECRET);
    }
}
