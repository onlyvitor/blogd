package com.onlyvitor.blogd.post.dto;

import com.onlyvitor.blogd.post.domain.AttachmentType;
import com.onlyvitor.blogd.post.domain.PostAttachment;

public record AttachmentResponse(
        Long id,
        AttachmentType type,
        String url,
        String name
) {

    public static AttachmentResponse from(PostAttachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getType(),
                attachment.getUrl(),
                attachment.getName()
        );
    }
}
