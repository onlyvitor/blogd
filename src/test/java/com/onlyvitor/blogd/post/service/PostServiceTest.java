package com.onlyvitor.blogd.post.service;

import com.onlyvitor.blogd.post.domain.AttachmentType;
import com.onlyvitor.blogd.post.domain.Post;
import com.onlyvitor.blogd.post.domain.PostFormat;
import com.onlyvitor.blogd.post.domain.PostVisibility;
import com.onlyvitor.blogd.post.dto.AttachmentRequest;
import com.onlyvitor.blogd.post.dto.AttachmentResponse;
import com.onlyvitor.blogd.post.dto.AuthorResponse;
import com.onlyvitor.blogd.post.dto.PostRequest;
import com.onlyvitor.blogd.post.dto.PostResponse;
import com.onlyvitor.blogd.post.dto.PostSummaryResponse;
import com.onlyvitor.blogd.post.exception.InvalidRelatedPostException;
import com.onlyvitor.blogd.post.exception.PostDeletionNotAllowedException;
import com.onlyvitor.blogd.post.exception.PostNotFoundException;
import com.onlyvitor.blogd.post.repository.PostRepository;
import com.onlyvitor.blogd.user.domain.Role;
import com.onlyvitor.blogd.user.domain.User;
import com.onlyvitor.blogd.user.exception.UserNotFoundException;
import com.onlyvitor.blogd.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostServiceTest {

    private static final long MAIN_AUTHOR = 1L;
    private static final long COAUTHOR = 2L;
    private static final long STRANGER = 3L;

    private PostRepository postRepository;
    private UserRepository userRepository;
    private PostKeyManager postKeyManager;
    private PostService postService;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        userRepository = mock(UserRepository.class);
        postKeyManager = new PostKeyManager();
        postService = new PostService(postRepository, userRepository, postKeyManager);
    }

    @Test
    void shouldCreatePublicPostWithDefaultsWhenOptionalFieldsAreMissing() {
        PostRequest request = new PostRequest("Título", "Conteúdo", null, null, MAIN_AUTHOR, null, null, null);
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.create(request, MAIN_AUTHOR);

        assertThat(response.format()).isEqualTo(PostFormat.MARKDOWN);
        assertThat(response.visibility()).isEqualTo(PostVisibility.PUBLIC);
        assertThat(response.key()).isNull();
        assertThat(response.author().id()).isEqualTo(MAIN_AUTHOR);
        assertThat(response.coauthors()).isEmpty();
        assertThat(response.relatedPosts()).isEmpty();
        assertThat(response.attachments()).isEmpty();
        assertThat(response.deletable()).isFalse();
    }

    @Test
    void shouldIgnoreAuthorListedAmongCoauthors() {
        PostRequest request = new PostRequest(
                "Título", "Conteúdo", null, null, MAIN_AUTHOR, Set.of(MAIN_AUTHOR, COAUTHOR), null, null);
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));
        when(userRepository.findAllById(Set.of(COAUTHOR))).thenReturn(List.of(user(COAUTHOR)));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.create(request, MAIN_AUTHOR);

        assertThat(response.coauthors()).extracting(AuthorResponse::id).containsExactly(COAUTHOR);
    }

    @Test
    void shouldCreatePrivatePostWithKeyAndCoauthors() {
        PostRequest request = new PostRequest(
                "Título", "Conteúdo", PostFormat.RST, PostVisibility.PRIVATE, MAIN_AUTHOR, Set.of(COAUTHOR), null, null);
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));
        when(userRepository.findAllById(Set.of(COAUTHOR))).thenReturn(List.of(user(COAUTHOR)));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.create(request, MAIN_AUTHOR);

        assertThat(response.visibility()).isEqualTo(PostVisibility.PRIVATE);
        assertThat(response.key()).isNotBlank();
        assertThat(response.format()).isEqualTo(PostFormat.RST);
        assertThat(response.coauthors()).extracting(AuthorResponse::id).containsExactly(COAUTHOR);
    }

    @Test
    void shouldCreatePostWithRelatedPostsAndAttachments() {
        PostRequest request = new PostRequest(
                "Título", "Conteúdo", null, null, MAIN_AUTHOR, null, Set.of(2L),
                List.of(new AttachmentRequest(AttachmentType.VIDEO, "https://example.com/video.mp4", "Demo")));
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));
        when(postRepository.findAllById(Set.of(2L))).thenReturn(List.of(post(2L, PostVisibility.PUBLIC, COAUTHOR, Set.of())));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.create(request, MAIN_AUTHOR);

        assertThat(response.relatedPosts()).extracting(PostSummaryResponse::id).containsExactly(2L);
        assertThat(response.attachments()).extracting(AttachmentResponse::type).containsExactly(AttachmentType.VIDEO);
        assertThat(response.attachments().get(0).name()).isEqualTo("Demo");
    }

    @Test
    void shouldRejectCreateWhenAuthorDoesNotExist() {
        PostRequest request = new PostRequest("Título", "Conteúdo", null, null, MAIN_AUTHOR, null, null, null);
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.create(request, MAIN_AUTHOR))
                .isInstanceOf(UserNotFoundException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void shouldRejectCreateWhenCoauthorDoesNotExist() {
        PostRequest request = new PostRequest("Título", "Conteúdo", null, null, MAIN_AUTHOR, Set.of(COAUTHOR), null, null);
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));
        when(userRepository.findAllById(Set.of(COAUTHOR))).thenReturn(List.of());

        assertThatThrownBy(() -> postService.create(request, MAIN_AUTHOR))
                .isInstanceOf(UserNotFoundException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void shouldRejectCreateWhenRelatedPostDoesNotExist() {
        PostRequest request = new PostRequest("Título", "Conteúdo", null, null, MAIN_AUTHOR, null, Set.of(99L), null);
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));
        when(postRepository.findAllById(Set.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> postService.create(request, MAIN_AUTHOR))
                .isInstanceOf(PostNotFoundException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void shouldRejectRelatingToPrivatePostThatRequesterCannotRead() {
        PostRequest request = new PostRequest("Título", "Conteúdo", null, null, MAIN_AUTHOR, null, Set.of(2L), null);
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));
        when(postRepository.findAllById(Set.of(2L)))
                .thenReturn(List.of(post(2L, PostVisibility.PRIVATE, STRANGER, Set.of())));

        assertThatThrownBy(() -> postService.create(request, MAIN_AUTHOR))
                .isInstanceOf(PostNotFoundException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void shouldHidePrivatePostFromStrangersUnlessTheyHaveTheKey() {
        Post post = post(1L, PostVisibility.PRIVATE, MAIN_AUTHOR, Set.of(COAUTHOR));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.findById(1L, STRANGER, null))
                .isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> postService.findById(1L, STRANGER, "wrong-key"))
                .isInstanceOf(PostNotFoundException.class);

        PostResponse byKey = postService.findById(1L, STRANGER, post.getKey());
        assertThat(byKey.key()).isNull();

        PostResponse byCoauthor = postService.findById(1L, COAUTHOR, null);
        assertThat(byCoauthor.key()).isEqualTo(post.getKey());
    }

    @Test
    void shouldRejectUpdateWhenPostDoesNotExist() {
        PostRequest request = new PostRequest("Título", "Conteúdo", null, null, MAIN_AUTHOR, null, null, null);
        when(postRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.update(1L, request, MAIN_AUTHOR))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void shouldRejectRelatingPostToItself() {
        Post existing = post(1L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of());
        PostRequest request = new PostRequest("Título", "Conteúdo", null, null, MAIN_AUTHOR, null, Set.of(1L), null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));

        assertThatThrownBy(() -> postService.update(1L, request, MAIN_AUTHOR))
                .isInstanceOf(InvalidRelatedPostException.class);
    }

    @Test
    void shouldReplaceContentAuthorsRelatedPostsAndAttachmentsWhenMainAuthorUpdates() {
        Post existing = post(1L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of());
        PostRequest request = new PostRequest(
                "Título novo", "Conteúdo novo", PostFormat.RST, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of(COAUTHOR),
                Set.of(3L),
                List.of(new AttachmentRequest(AttachmentType.IMAGE, "https://example.com/new.png", "Capa")));
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));
        when(userRepository.findAllById(Set.of(COAUTHOR))).thenReturn(List.of(user(COAUTHOR)));
        when(postRepository.findAllById(Set.of(3L))).thenReturn(List.of(post(3L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of())));

        PostResponse response = postService.update(1L, request, MAIN_AUTHOR);

        assertThat(response.title()).isEqualTo("Título novo");
        assertThat(response.content()).isEqualTo("Conteúdo novo");
        assertThat(response.format()).isEqualTo(PostFormat.RST);
        assertThat(response.author().id()).isEqualTo(MAIN_AUTHOR);
        assertThat(response.coauthors()).extracting(AuthorResponse::id).containsExactly(COAUTHOR);
        assertThat(response.relatedPosts()).extracting(PostSummaryResponse::id).containsExactly(3L);
        assertThat(response.attachments()).extracting(AttachmentResponse::url)
                .containsExactly("https://example.com/new.png");
    }

    @Test
    void shouldAllowCoauthorToUpdateContentWhileKeepingAuthors() {
        Post existing = post(1L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of(COAUTHOR));
        PostRequest request = new PostRequest(
                "Revisado", "Conteúdo", null, null, MAIN_AUTHOR, Set.of(COAUTHOR), null, null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));
        when(userRepository.findAllById(Set.of(COAUTHOR))).thenReturn(List.of(user(COAUTHOR)));

        PostResponse response = postService.update(1L, request, COAUTHOR);

        assertThat(response.title()).isEqualTo("Revisado");
        assertThat(response.author().id()).isEqualTo(MAIN_AUTHOR);
    }

    @Test
    void shouldRejectAuthorshipChangeFromCoauthor() {
        Post existing = post(1L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of(COAUTHOR));
        PostRequest request = new PostRequest("Título", "Conteúdo", null, null, MAIN_AUTHOR, Set.of(), null, null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> postService.update(1L, request, COAUTHOR))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(existing.getCoauthors()).extracting(User::getId).containsExactly(COAUTHOR);
    }

    @Test
    void shouldRejectDeleteWhenMajorityHasNotAccepted() {
        Post existing = post(1L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of(COAUTHOR));
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> postService.delete(1L))
                .isInstanceOf(PostDeletionNotAllowedException.class);

        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void shouldDeletePostWhenMajorityHasAccepted() {
        Post existing = post(1L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of(COAUTHOR));
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        postService.acceptDeletion(1L, MAIN_AUTHOR);

        postService.delete(1L);

        verify(postRepository).delete(existing);
    }

    @Test
    void shouldRejectDeleteWhenPostDoesNotExist() {
        when(postRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.delete(1L))
                .isInstanceOf(PostNotFoundException.class);

        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void shouldRecordAndWithdrawDeletionAcceptance() {
        Post existing = post(1L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of(COAUTHOR));
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));

        PostResponse accepted = postService.acceptDeletion(1L, MAIN_AUTHOR);

        assertThat(accepted.deletionAcceptances()).extracting(AuthorResponse::id).containsExactly(MAIN_AUTHOR);
        assertThat(accepted.deletable()).isTrue();

        PostResponse withdrawn = postService.withdrawDeletionAcceptance(1L, MAIN_AUTHOR);

        assertThat(withdrawn.deletionAcceptances()).isEmpty();
        assertThat(withdrawn.deletable()).isFalse();
    }

    @Test
    void shouldRejectDeletionAcceptanceFromNonAuthor() {
        Post existing = post(1L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of(COAUTHOR));
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> postService.acceptDeletion(1L, STRANGER))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(existing.getDeletionAcceptances()).isEmpty();
    }

    @Test
    void shouldKeepAccessKeyWhenPrivatePostIsUpdated() {
        Post existing = post(1L, PostVisibility.PRIVATE, MAIN_AUTHOR, Set.of());
        String key = existing.getKey();
        PostRequest request = new PostRequest(
                "Título", "Conteúdo", null, PostVisibility.PRIVATE, MAIN_AUTHOR, null, null, null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));

        PostResponse response = postService.update(1L, request, MAIN_AUTHOR);

        assertThat(response.key()).isEqualTo(key);
    }

    @Test
    void shouldDropAccessKeyWhenPostBecomesPublic() {
        Post existing = post(1L, PostVisibility.PRIVATE, MAIN_AUTHOR, Set.of());
        PostRequest request = new PostRequest(
                "Título", "Conteúdo", null, PostVisibility.PUBLIC, MAIN_AUTHOR, null, null, null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(MAIN_AUTHOR)).thenReturn(Optional.of(user(MAIN_AUTHOR)));

        PostResponse response = postService.update(1L, request, MAIN_AUTHOR);

        assertThat(response.key()).isNull();
    }

    @Test
    void shouldPromoteCoauthorWhenMainAuthorIsRemoved() {
        Post existing = post(1L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of(COAUTHOR));
        when(postRepository.findAllByAuthorId(MAIN_AUTHOR)).thenReturn(List.of(existing));

        postService.handleAuthorDeletion(MAIN_AUTHOR);

        assertThat(existing.getAuthor().getId()).isEqualTo(COAUTHOR);
        assertThat(existing.getCoauthors()).isEmpty();
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void shouldDeletePostWithoutOtherAuthorsWhenItsMainAuthorIsRemoved() {
        Post existing = post(1L, PostVisibility.PUBLIC, MAIN_AUTHOR, Set.of());
        when(postRepository.findAllByAuthorId(MAIN_AUTHOR)).thenReturn(List.of(existing));

        postService.handleAuthorDeletion(MAIN_AUTHOR);

        verify(postRepository).delete(existing);
    }

    private Post post(Long id, PostVisibility visibility, long authorId, Set<Long> coauthorIds) {
        PostRequest request = new PostRequest(
                "Post " + id, "Conteúdo", null, visibility, authorId, coauthorIds, null, null);
        Set<User> coauthors = coauthorIds.stream().map(this::user).collect(Collectors.toSet());
        Post post = Post.from(
                request, user(authorId), coauthors, Set.of(), postKeyManager.resolveKey(visibility, null));
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private User user(long id) {
        return User.builder()
                .id(id)
                .username("user" + id)
                .password("encoded")
                .email("user" + id + "@example.com")
                .role(Role.USER)
                .build();
    }
}
