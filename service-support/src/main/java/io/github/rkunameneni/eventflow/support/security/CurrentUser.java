package io.github.rkunameneni.eventflow.support.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** The authenticated caller, reduced to what the services need for ownership checks. */
public record CurrentUser(String id, boolean admin) {

    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    public static CurrentUser from(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
        return new CurrentUser(authentication.getName(), admin);
    }

    /** Admins can act on any customer's data; everyone else only on their own. */
    public boolean canAccess(String ownerId) {
        return admin || id.equals(ownerId);
    }
}
