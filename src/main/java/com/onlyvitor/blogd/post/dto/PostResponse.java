package com.onlyvitor.blogd.post.dto;

import com.onlyvitor.blogd.post.domain.Post;
import com.onlyvitor.blogd.post.domain.PostFormat;

import java.time.Instant;
import java.util.List;

public record PostResponse(
        Long id,
        String title,
        String content,
        PostFormat format,
        List<AuthorResponse> authors,
        List<PostSummaryResponse> relatedPosts,
        List<AttachmentResponse> attachments,
        Instant createdAt,
        Instant updatedAt
) {

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getFormat(),
                post.getAuthors().stream().map(AuthorResponse::from).toList(),
                post.getRelatedPosts().stream().map(PostSummaryResponse::from).toList(),
                post.getAttachments().stream().map(AttachmentResponse::from).toList(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
