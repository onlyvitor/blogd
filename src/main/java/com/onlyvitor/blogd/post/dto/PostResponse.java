package com.onlyvitor.blogd.post.dto;

import com.onlyvitor.blogd.post.domain.Post;
import com.onlyvitor.blogd.post.domain.PostFormat;
import com.onlyvitor.blogd.post.domain.PostVisibility;

import java.time.Instant;
import java.util.List;

public record PostResponse(
        Long id,
        String title,
        String content,
        PostFormat format,
        PostVisibility visibility,
        String key,
        AuthorResponse author,
        List<AuthorResponse> coauthors,
        List<AuthorResponse> deletionAcceptances,
        boolean deletable,
        List<PostSummaryResponse> relatedPosts,
        List<AttachmentResponse> attachments,
        Instant createdAt,
        Instant updatedAt
) {

    public static PostResponse from(Post post, long viewerId) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getFormat(),
                post.getVisibility(),
                post.hasAuthor(viewerId) ? post.getKey() : null,
                AuthorResponse.from(post.getAuthor()),
                post.getCoauthors().stream().map(AuthorResponse::from).toList(),
                post.getDeletionAcceptances().stream().map(AuthorResponse::from).toList(),
                post.isDeletable(),
                post.getRelatedPosts().stream()
                        .filter(related -> related.isReadableBy(viewerId))
                        .map(PostSummaryResponse::from)
                        .toList(),
                post.getAttachments().stream().map(AttachmentResponse::from).toList(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
