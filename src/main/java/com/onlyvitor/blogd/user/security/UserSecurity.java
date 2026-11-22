package com.onlyvitor.blogd.user.security;

import com.onlyvitor.blogd.auth.identity.UserAuthenticated;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("userSecurity")
public class UserSecurity {

    public boolean isSelf(Authentication authentication, Long id) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserAuthenticated userAuthenticated) {
            return id.equals(userAuthenticated.getId());
        }
        if (principal instanceof Jwt jwt) {
            Object userId = jwt.getClaim("userId");
            return userId instanceof Number number && id == number.longValue();
        }
        return false;
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
