package com.onlyvitor.blogd.auth.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.onlyvitor.blogd.auth.domain.RefreshToken;
import com.onlyvitor.blogd.auth.dto.TokenResponse;
import com.onlyvitor.blogd.auth.exception.InvalidCredentialsException;
import com.onlyvitor.blogd.auth.identity.UserAuthenticated;
import com.onlyvitor.blogd.user.domain.Role;
import com.onlyvitor.blogd.user.domain.User;
import com.onlyvitor.blogd.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final long ACCESS_TOKEN_EXPIRY_SECONDS = 900L;

    private JwtDecoder jwtDecoder;
    private RefreshTokenService refreshTokenService;
    private UserRepository userRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        RSAPrivateKey privateKey = RsaKeyConverters.pkcs8()
                .convert(new ClassPathResource("app.key").getInputStream());
        RSAPublicKey publicKey = RsaKeyConverters.x509()
                .convert(new ClassPathResource("app.pub").getInputStream());

        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        refreshTokenService = mock(RefreshTokenService.class);
        userRepository = mock(UserRepository.class);
        authService = new AuthService(jwtEncoder, refreshTokenService, userRepository);
        ReflectionTestUtils.setField(authService, "accessTokenExpirySeconds", ACCESS_TOKEN_EXPIRY_SECONDS);
    }

    @Test
    void shouldIssueAccessAndRefreshTokensOnLogin() {
        UserAuthenticated principal = new UserAuthenticated(user(Role.ADMIN));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        when(refreshTokenService.create(1L)).thenReturn("refresh-token");

        TokenResponse response = authService.login(authentication);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(ACCESS_TOKEN_EXPIRY_SECONDS);
        assertThat(response.user().getId()).isEqualTo(1L);
        assertThat(response.user().getUsername()).isEqualTo("vitor");
        assertThat(response.user().getEmail()).isEqualTo("vitor@example.com");
        assertThat(response.user().getRole()).isEqualTo(Role.ADMIN);

        Jwt jwt = jwtDecoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("vitor");
        assertThat(jwt.<String>getClaim("scopes")).isEqualTo("ROLE_ADMIN");
        Number userId = jwt.getClaim("userId");
        assertThat(userId.longValue()).isEqualTo(1L);
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void shouldRejectLoginWithoutAuthenticatedPrincipal() {
        assertThatThrownBy(() -> authService.login(null))
                .isInstanceOf(InvalidCredentialsException.class);

        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThatThrownBy(() -> authService.login(anonymous))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldIssueNewTokensWhenRefreshing() {
        User user = user(Role.USUARIO);
        when(refreshTokenService.validateAndRotate("old-refresh-token"))
                .thenReturn(Optional.of(RefreshToken.builder().userId(1L).build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenService.create(1L)).thenReturn("new-refresh-token");

        Optional<TokenResponse> response = authService.refresh("old-refresh-token");

        assertThat(response).isPresent();
        assertThat(response.get().accessToken()).isNotBlank();
        assertThat(response.get().refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.get().user().getUsername()).isEqualTo("vitor");
    }

    @Test
    void shouldRejectRefreshWhenTokenIsInvalid() {
        when(refreshTokenService.validateAndRotate("invalid"))
                .thenReturn(Optional.empty());

        assertThat(authService.refresh("invalid")).isEmpty();
    }

    @Test
    void shouldRejectRefreshWhenUserNoLongerExists() {
        when(refreshTokenService.validateAndRotate("old-refresh-token"))
                .thenReturn(Optional.of(RefreshToken.builder().userId(1L).build()));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(authService.refresh("old-refresh-token")).isEmpty();
    }

    @Test
    void shouldDelegateLogoutToRefreshTokenService() {
        authService.logout("refresh-token");

        verify(refreshTokenService).revoke("refresh-token");
    }

    private User user(Role role) {
        return User.builder()
                .id(1L)
                .username("vitor")
                .password("encoded")
                .email("vitor@example.com")
                .role(role)
                .build();
    }
}
