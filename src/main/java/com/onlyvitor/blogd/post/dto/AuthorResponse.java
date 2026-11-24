package com.onlyvitor.blogd.post.dto;

import com.onlyvitor.blogd.user.domain.User;

public record AuthorResponse(
        Long id,
        String username
) {

    public static AuthorResponse from(User user) {
        return new AuthorResponse(user.getId(), user.getUsername());
    }
}
