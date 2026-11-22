package com.onlyvitor.blogd.auth.controller;

import com.onlyvitor.blogd.auth.exception.InvalidCredentialsException;
import com.onlyvitor.blogd.auth.identity.UserAuthenticated;
import com.onlyvitor.blogd.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping
    public String authenticate(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserAuthenticated)) {
            throw new InvalidCredentialsException();
        }
        return authService.authenticate(authentication);
    }
}
