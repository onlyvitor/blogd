package com.onlyvitor.blogd.post.dto;

import com.onlyvitor.blogd.post.domain.AttachmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AttachmentRequest(
        @NotNull(message = "Attachment type is required")
        AttachmentType type,

        @NotBlank(message = "Attachment URL is required")
        @Size(max = 2048, message = "Attachment URL must be at most 2048 characters")
        @Pattern(regexp = "^https?://\\S+$", message = "Attachment URL must start with http:// or https://")
        String url,

        @Size(max = 255, message = "Attachment name must be at most 255 characters")
        String name
) {
}
