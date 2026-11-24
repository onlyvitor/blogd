package com.onlyvitor.blogd.auth.service;

import com.onlyvitor.blogd.auth.domain.RefreshToken;
import com.onlyvitor.blogd.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final long EXPIRY_SECONDS = 604800L;

    private RefreshTokenRepository refreshTokenRepository;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirySeconds", EXPIRY_SECONDS);
    }

    @Test
    void shouldStoreOnlyTheHashWhenCreatingToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.create(1L);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(rawToken).isNotBlank();
        assertThat(saved.getTokenHash())
                .isEqualTo(sha256(rawToken))
                .isNotEqualTo(rawToken)
                .hasSize(64);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusSeconds(EXPIRY_SECONDS - 60));
    }

    @Test
    void shouldRotateValidToken() {
        String rawToken = "valid-refresh-token";
        RefreshToken stored = activeToken(rawToken);
        when(refreshTokenRepository.findByTokenHashForUpdate(sha256(rawToken)))
                .thenReturn(Optional.of(stored));

        Optional<RefreshToken> result = refreshTokenService.validateAndRotate(rawToken);

        assertThat(result).containsSame(stored);
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(stored);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void shouldRejectUnknownToken() {
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.empty());

        assertThat(refreshTokenService.validateAndRotate("unknown")).isEmpty();
    }

    @Test
    void shouldRejectExpiredToken() {
        String rawToken = "expired-refresh-token";
        RefreshToken stored = activeToken(rawToken);
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(sha256(rawToken)))
                .thenReturn(Optional.of(stored));

        assertThat(refreshTokenService.validateAndRotate(rawToken)).isEmpty();

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldRevokeAllSessionsWhenRotatedTokenIsReused() {
        String rawToken = "reused-refresh-token";
        RefreshToken stored = activeToken(rawToken);
        stored.setRevokedAt(Instant.now().minusSeconds(5));
        when(refreshTokenRepository.findByTokenHashForUpdate(sha256(rawToken)))
                .thenReturn(Optional.of(stored));

        assertThat(refreshTokenService.validateAndRotate(rawToken)).isEmpty();

        verify(refreshTokenRepository).revokeAllByUserId(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldRevokeActiveTokenOnLogout() {
        String rawToken = "logout-refresh-token";
        RefreshToken stored = activeToken(rawToken);
        when(refreshTokenRepository.findByTokenHash(sha256(rawToken)))
                .thenReturn(Optional.of(stored));

        refreshTokenService.revoke(rawToken);

        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void shouldIgnoreLogoutForUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.empty());

        refreshTokenService.revoke("unknown");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldRevokeAllTokensForUser() {
        refreshTokenService.revokeAllForUser(1L);

        verify(refreshTokenRepository).revokeAllByUserId(any(), any());
    }

    private RefreshToken activeToken(String rawToken) {
        return RefreshToken.builder()
                .id(1L)
                .tokenHash(sha256(rawToken))
                .userId(1L)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(EXPIRY_SECONDS))
                .build();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
