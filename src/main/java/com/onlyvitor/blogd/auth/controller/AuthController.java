package com.onlyvitor.blogd.auth.controller;

import com.onlyvitor.blogd.auth.dto.AuthRequest;
import com.onlyvitor.blogd.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("auth")
    public String authenticate(Authentication auth){
        return authService.authenticate(auth);
    }
}
