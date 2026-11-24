package com.onlyvitor.blogd.auth.dto;

import com.onlyvitor.blogd.user.dto.UserResponse;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
