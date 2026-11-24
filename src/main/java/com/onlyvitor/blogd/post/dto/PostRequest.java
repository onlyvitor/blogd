package com.onlyvitor.blogd.post.dto;

import com.onlyvitor.blogd.post.domain.PostFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record PostRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @NotBlank(message = "Content is required")
        String content,

        PostFormat format,

        @NotEmpty(message = "At least one author is required")
        Set<@NotNull(message = "Author id must not be null") Long> authorIds,

        Set<@NotNull(message = "Related post id must not be null") Long> relatedPostIds,

        List<@Valid @NotNull(message = "Attachment must not be null") AttachmentRequest> attachments
) {

    public PostRequest {
        format = format != null ? format : PostFormat.MARKDOWN;
        relatedPostIds = relatedPostIds != null ? relatedPostIds : Set.of();
        attachments = attachments != null ? attachments : List.of();
    }
}
