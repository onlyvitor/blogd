package com.onlyvitor.blogd.auth.service;

import com.onlyvitor.blogd.auth.domain.RefreshToken;
import com.onlyvitor.blogd.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token.expiry-seconds:604800}")
    private long refreshTokenExpirySeconds;

    @Transactional
    public String create(Long userId) {
        byte[] randomBytes = new byte[64];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = BASE64_ENCODER.encodeToString(randomBytes);

        Instant now = Instant.now();
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .userId(userId)
                .expiresAt(now.plusSeconds(refreshTokenExpirySeconds))
                .createdAt(now)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public Optional<RefreshToken> validateAndRotate(String rawToken) {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken stored = found.get();
        if (stored.getRevokedAt() != null) {
            // Reuse of a rotated token: assume it was stolen and revoke the whole session family.
            refreshTokenRepository.revokeAllByUserId(stored.getUserId(), Instant.now());
            return Optional.empty();
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }

        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);
        return Optional.of(stored);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteExpired(Instant.now());
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
