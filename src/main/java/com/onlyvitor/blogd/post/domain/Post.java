package com.onlyvitor.blogd.post.domain;

import com.onlyvitor.blogd.post.dto.PostRequest;
import com.onlyvitor.blogd.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostFormat format;

    @ManyToMany
    @JoinTable(
            name = "post_authors",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @OrderBy("id ASC")
    private Set<User> authors = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "post_related_posts",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "related_post_id"))
    @OrderBy("id ASC")
    private Set<Post> relatedPosts = new LinkedHashSet<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PostAttachment> attachments = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Post from(PostRequest request, Set<User> authors, Set<Post> relatedPosts) {
        Post post = new Post();
        post.update(request, authors, relatedPosts);
        return post;
    }

    public void update(PostRequest request, Set<User> authors, Set<Post> relatedPosts) {
        this.title = request.title();
        this.content = request.content();
        this.format = request.format();

        this.authors.clear();
        this.authors.addAll(authors);

        this.relatedPosts.clear();
        this.relatedPosts.addAll(relatedPosts);

        this.attachments.clear();
        request.attachments().forEach(attachment -> this.attachments.add(PostAttachment.from(attachment, this)));
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
