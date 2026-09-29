package io.github.rkunameneni.eventflow.support.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

class CurrentUserTest {

    @Test
    void customersMayOnlyAccessTheirOwnData() {
        CurrentUser alice = CurrentUser.from(new TestingAuthenticationToken("alice", null, "ROLE_CUSTOMER"));

        assertThat(alice.admin()).isFalse();
        assertThat(alice.canAccess("alice")).isTrue();
        assertThat(alice.canAccess("bob")).isFalse();
    }

    @Test
    void adminsMayAccessAnyCustomer() {
        CurrentUser admin = CurrentUser.from(new TestingAuthenticationToken("ops", null, "ROLE_ADMIN"));

        assertThat(admin.admin()).isTrue();
        assertThat(admin.canAccess("bob")).isTrue();
    }
}
