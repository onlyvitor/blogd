package com.onlyvitor.blogd.user.security;

import com.onlyvitor.blogd.auth.identity.UserAuthenticated;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("userSecurity")
public class UserSecurity {

    public boolean isSelf(Authentication authentication, Long id) {
        return currentUserId(authentication).map(id::equals).orElse(false);
    }

    public Optional<Long> currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserAuthenticated userAuthenticated) {
            return Optional.of(userAuthenticated.getId());
        }
        if (principal instanceof Jwt jwt && jwt.getClaim("userId") instanceof Number number) {
            return Optional.of(number.longValue());
        }
        return Optional.empty();
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
