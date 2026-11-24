package com.onlyvitor.blogd.post.domain;

import com.onlyvitor.blogd.post.dto.AttachmentRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_attachments")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttachmentType type;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 255)
    private String name;

    public static PostAttachment from(AttachmentRequest request, Post post) {
        return PostAttachment.builder()
                .post(post)
                .type(request.type())
                .url(request.url())
                .name(request.name())
                .build();
    }
}
