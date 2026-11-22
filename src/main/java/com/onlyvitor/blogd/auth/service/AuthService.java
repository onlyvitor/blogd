package com.onlyvitor.blogd.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtEncoder encoder;

    private String generateToken(Authentication auth){
        Instant now = Instant.now();
        long expiry = 3600L;

        String scopes = auth.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        var claim = JwtClaimsSet.builder()
                .issuer(auth.getName())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiry))
                .subject(auth.getName())
                .claim("scopes", scopes)
                .build();

        return encoder.encode(JwtEncoderParameters.from(claim)).getTokenValue();
    }


    public String authenticate(Authentication auth) {
        return generateToken(auth);
    }
}
