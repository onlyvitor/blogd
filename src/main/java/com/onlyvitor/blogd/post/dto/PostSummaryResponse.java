package com.onlyvitor.blogd.post.dto;

import com.onlyvitor.blogd.post.domain.Post;

public record PostSummaryResponse(
        Long id,
        String title
) {

    public static PostSummaryResponse from(Post post) {
        return new PostSummaryResponse(post.getId(), post.getTitle());
    }
}
