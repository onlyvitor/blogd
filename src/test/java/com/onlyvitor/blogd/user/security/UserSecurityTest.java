package com.onlyvitor.blogd.user.security;

import com.onlyvitor.blogd.auth.identity.UserAuthenticated;
import com.onlyvitor.blogd.user.domain.Role;
import com.onlyvitor.blogd.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserSecurityTest {

    private final UserSecurity userSecurity = new UserSecurity();

    @Test
    void shouldMatchAuthenticatedUserById() {
        UserAuthenticated principal = new UserAuthenticated(user(1L, Role.USUARIO));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        assertThat(userSecurity.isSelf(authentication, 1L)).isTrue();
        assertThat(userSecurity.isSelf(authentication, 2L)).isFalse();
    }

    @Test
    void shouldMatchJwtByUserIdClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .subject("vitor")
                .claim("userId", 1L)
                .build();
        Authentication authentication = new JwtAuthenticationToken(jwt, List.of());

        assertThat(userSecurity.isSelf(authentication, 1L)).isTrue();
        assertThat(userSecurity.isSelf(authentication, 2L)).isFalse();
    }

    @Test
    void shouldIdentifyAdmin() {
        UserAuthenticated principal = new UserAuthenticated(user(1L, Role.ADMIN));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        assertThat(userSecurity.isAdmin(authentication)).isTrue();
    }

    @Test
    void shouldNotIdentifyRegularUserAsAdmin() {
        UserAuthenticated principal = new UserAuthenticated(user(1L, Role.USUARIO));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        assertThat(userSecurity.isAdmin(authentication)).isFalse();
    }

    @Test
    void shouldRejectNullAuthentication() {
        assertThat(userSecurity.isSelf(null, 1L)).isFalse();
        assertThat(userSecurity.isAdmin(null)).isFalse();
    }

    private User user(Long id, Role role) {
        return User.builder()
                .id(id)
                .username("vitor")
                .password("encoded")
                .email("vitor@example.com")
                .role(role)
                .build();
    }
}
