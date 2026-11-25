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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostVisibility visibility;

    @Column(name = "access_key", length = 64)
    private String key;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToMany
    @JoinTable(
            name = "post_coauthors",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @OrderBy("id ASC")
    private Set<User> coauthors = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "post_deletion_acceptances",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @OrderBy("id ASC")
    private Set<User> deletionAcceptances = new LinkedHashSet<>();

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

    public static Post from(
            PostRequest request, User author, Set<User> coauthors, Set<Post> relatedPosts, String key) {
        Post post = new Post();
        post.update(request, author, coauthors, relatedPosts, key);
        return post;
    }

    public void update(
            PostRequest request, User author, Set<User> coauthors, Set<Post> relatedPosts, String key) {
        this.title = request.title();
        this.content = request.content();
        this.format = request.format();
        this.visibility = request.visibility();
        this.key = visibility == PostVisibility.PRIVATE
                ? Objects.requireNonNull(key, "Private posts need an access key")
                : null;
        this.author = author;

        this.coauthors.clear();
        this.coauthors.addAll(coauthors);

        this.deletionAcceptances.removeIf(user -> !hasAuthor(user.getId()));

        this.relatedPosts.clear();
        this.relatedPosts.addAll(relatedPosts);

        this.attachments.clear();
        request.attachments().forEach(attachment -> this.attachments.add(PostAttachment.from(attachment, this)));
    }

    public boolean hasAuthor(long userId) {
        return author.getId() == userId || coauthors.stream().anyMatch(coauthor -> coauthor.getId() == userId);
    }

    public boolean isMainAuthor(long userId) {
        return author.getId() == userId;
    }

    public boolean hasSameAuthors(long authorId, Set<Long> coauthorIds) {
        return author.getId() == authorId
                && coauthors.stream().map(User::getId).collect(Collectors.toSet()).equals(coauthorIds);
    }

    public boolean isReadableBy(long userId) {
        return visibility == PostVisibility.PUBLIC || hasAuthor(userId);
    }

    public void addDeletionAcceptance(User acceptor) {
        if (!hasAcceptedDeletion(acceptor.getId())) {
            deletionAcceptances.add(acceptor);
        }
    }

    public void removeDeletionAcceptance(long userId) {
        deletionAcceptances.removeIf(user -> user.getId() == userId);
    }

    // More than half of the authors must accept; on a tie, the main author's decision prevails.
    public boolean isDeletable() {
        int authors = coauthors.size() + 1;
        int accepted = deletionAcceptances.size();
        if (accepted * 2 > authors) {
            return true;
        }
        return accepted * 2 == authors && hasAcceptedDeletion(author.getId());
    }

    public void promoteCoauthorToAuthor() {
        User newAuthor = coauthors.stream()
                .min(Comparator.comparing(User::getId))
                .orElseThrow(() -> new IllegalStateException("Post has no coauthor to promote"));
        long departingAuthorId = author.getId();
        coauthors.remove(newAuthor);
        deletionAcceptances.removeIf(user -> user.getId() == departingAuthorId);
        author = newAuthor;
    }

    private boolean hasAcceptedDeletion(long userId) {
        return deletionAcceptances.stream().anyMatch(user -> user.getId() == userId);
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
