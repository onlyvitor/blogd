package com.onlyvitor.blogd.post.service;

import com.onlyvitor.blogd.post.domain.PostVisibility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostKeyManagerTest {

    private final PostKeyManager postKeyManager = new PostKeyManager();

    @Test
    void shouldGenerateUrlSafeKeysThatDoNotRepeat() {
        String first = postKeyManager.generate();
        String second = postKeyManager.generate();

        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void shouldResolveKeyOnlyForPrivatePosts() {
        assertThat(postKeyManager.resolveKey(PostVisibility.PUBLIC, "chave")).isNull();
        assertThat(postKeyManager.resolveKey(PostVisibility.PRIVATE, "chave")).isEqualTo("chave");
        assertThat(postKeyManager.resolveKey(PostVisibility.PRIVATE, null)).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void shouldMatchOnlyTheExactKey() {
        assertThat(postKeyManager.matches("chave", "chave")).isTrue();
        assertThat(postKeyManager.matches("chave", "chave-errada")).isFalse();
        assertThat(postKeyManager.matches("chave", null)).isFalse();
        assertThat(postKeyManager.matches(null, "chave")).isFalse();
        assertThat(postKeyManager.matches(null, null)).isFalse();
    }
}
