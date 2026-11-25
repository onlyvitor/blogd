package com.onlyvitor.blogd.post.dto;

import com.onlyvitor.blogd.post.domain.PostFormat;
import com.onlyvitor.blogd.post.domain.PostVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record PostRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @NotBlank(message = "Content is required")
        String content,

        PostFormat format,

        PostVisibility visibility,

        @NotNull(message = "Author id is required")
        Long authorId,

        Set<@NotNull(message = "Coauthor id must not be null") Long> coauthorIds,

        Set<@NotNull(message = "Related post id must not be null") Long> relatedPostIds,

        List<@Valid @NotNull(message = "Attachment must not be null") AttachmentRequest> attachments
) {

    public PostRequest {
        format = format != null ? format : PostFormat.MARKDOWN;
        visibility = visibility != null ? visibility : PostVisibility.PUBLIC;
        coauthorIds = coauthorIds != null ? withoutAuthor(coauthorIds, authorId) : Set.of();
        relatedPostIds = relatedPostIds != null ? relatedPostIds : Set.of();
        attachments = attachments != null ? attachments : List.of();
    }

    private static Set<Long> withoutAuthor(Set<Long> coauthorIds, Long authorId) {
        return coauthorIds.stream()
                .filter(coauthorId -> !Objects.equals(coauthorId, authorId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
