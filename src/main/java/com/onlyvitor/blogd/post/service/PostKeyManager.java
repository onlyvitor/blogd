package com.onlyvitor.blogd.post.service;

import com.onlyvitor.blogd.post.domain.PostVisibility;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PostKeyManager {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int KEY_BYTES = 32;

    public String generate() {
        byte[] randomBytes = new byte[KEY_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return BASE64_ENCODER.encodeToString(randomBytes);
    }

    public String resolveKey(PostVisibility visibility, String currentKey) {
        if (visibility != PostVisibility.PRIVATE) {
            return null;
        }
        return currentKey != null ? currentKey : generate();
    }

    public boolean matches(String key, String candidate) {
        return key != null && candidate != null && MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }
}
