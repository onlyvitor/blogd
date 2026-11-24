package com.onlyvitor.blogd.auth.service;

import com.onlyvitor.blogd.auth.dto.TokenResponse;
import com.onlyvitor.blogd.auth.exception.InvalidCredentialsException;
import com.onlyvitor.blogd.auth.identity.UserAuthenticated;
import com.onlyvitor.blogd.user.dto.UserResponse;
import com.onlyvitor.blogd.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final JwtEncoder encoder;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @Value("${jwt.access-token.expiry-seconds:900}")
    private long accessTokenExpirySeconds;

    @Transactional
    public TokenResponse login(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserAuthenticated principal)) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(principal);
    }

    @Transactional
    public Optional<TokenResponse> refresh(String rawRefreshToken) {
        return refreshTokenService.validateAndRotate(rawRefreshToken)
                .flatMap(stored -> userRepository.findById(stored.getUserId()))
                .map(user -> issueTokens(new UserAuthenticated(user)));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private TokenResponse issueTokens(UserAuthenticated principal) {
        String accessToken = generateAccessToken(principal);
        String refreshToken = refreshTokenService.create(principal.getId());

        UserResponse user = UserResponse.from(principal);

        return new TokenResponse(accessToken, refreshToken, "Bearer", accessTokenExpirySeconds, user);
    }

    private String generateAccessToken(UserAuthenticated principal) {
        Instant now = Instant.now();

        String scopes = principal.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(principal.getUsername())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenExpirySeconds))
                .subject(principal.getUsername())
                .claim("scopes", scopes)
                .claim("userId", principal.getId())
                .build();

        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
