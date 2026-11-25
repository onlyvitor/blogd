package com.onlyvitor.blogd.post.service;

import com.onlyvitor.blogd.post.domain.AttachmentType;
import com.onlyvitor.blogd.post.domain.Post;
import com.onlyvitor.blogd.post.domain.PostFormat;
import com.onlyvitor.blogd.post.dto.AttachmentRequest;
import com.onlyvitor.blogd.post.dto.AttachmentResponse;
import com.onlyvitor.blogd.post.dto.AuthorResponse;
import com.onlyvitor.blogd.post.dto.PostRequest;
import com.onlyvitor.blogd.post.dto.PostResponse;
import com.onlyvitor.blogd.post.dto.PostSummaryResponse;
import com.onlyvitor.blogd.post.exception.InvalidRelatedPostException;
import com.onlyvitor.blogd.post.exception.PostNotFoundException;
import com.onlyvitor.blogd.post.repository.PostRepository;
import com.onlyvitor.blogd.user.domain.Role;
import com.onlyvitor.blogd.user.domain.User;
import com.onlyvitor.blogd.user.exception.UserNotFoundException;
import com.onlyvitor.blogd.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostServiceTest {

    private PostRepository postRepository;
    private UserRepository userRepository;
    private PostService postService;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        userRepository = mock(UserRepository.class);
        postService = new PostService(postRepository, userRepository);
    }

    @Test
    void shouldCreatePostWithMarkdownFormatWhenFormatIsNotProvided() {
        PostRequest request = new PostRequest("Título", "Conteúdo", null, Set.of(1L), null, null);
        when(userRepository.findAllById(Set.of(1L))).thenReturn(List.of(user(1L)));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.create(request);

        assertThat(response.format()).isEqualTo(PostFormat.MARKDOWN);
        assertThat(response.authors()).extracting(AuthorResponse::id).containsExactly(1L);
        assertThat(response.relatedPosts()).isEmpty();
        assertThat(response.attachments()).isEmpty();
    }

    @Test
    void shouldCreatePostWithRelatedPostsAndAttachments() {
        PostRequest request = new PostRequest(
                "Título", "Conteúdo", PostFormat.RST, Set.of(1L), Set.of(2L),
                List.of(new AttachmentRequest(AttachmentType.VIDEO, "https://example.com/video.mp4", "Demo")));
        when(userRepository.findAllById(Set.of(1L))).thenReturn(List.of(user(1L)));
        when(postRepository.findAllById(Set.of(2L))).thenReturn(List.of(post(2L, null)));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.create(request);

        assertThat(response.format()).isEqualTo(PostFormat.RST);
        assertThat(response.relatedPosts()).extracting(PostSummaryResponse::id).containsExactly(2L);
        assertThat(response.attachments()).extracting(AttachmentResponse::type).containsExactly(AttachmentType.VIDEO);
        assertThat(response.attachments().get(0).name()).isEqualTo("Demo");
    }

    @Test
    void shouldRejectCreateWhenAuthorDoesNotExist() {
        PostRequest request = new PostRequest("Título", "Conteúdo", null, Set.of(1L, 2L), null, null);
        when(userRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(user(1L)));

        assertThatThrownBy(() -> postService.create(request))
                .isInstanceOf(UserNotFoundException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void shouldRejectCreateWhenRelatedPostDoesNotExist() {
        PostRequest request = new PostRequest("Título", "Conteúdo", null, Set.of(1L), Set.of(99L), null);
        when(userRepository.findAllById(Set.of(1L))).thenReturn(List.of(user(1L)));
        when(postRepository.findAllById(Set.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> postService.create(request))
                .isInstanceOf(PostNotFoundException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void shouldRejectRelatingPostToItself() {
        Post existing = post(1L, null);
        PostRequest request = new PostRequest("Título", "Conteúdo", null, Set.of(1L), Set.of(1L), null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findAllById(Set.of(1L))).thenReturn(List.of(user(1L)));

        assertThatThrownBy(() -> postService.update(1L, request))
                .isInstanceOf(InvalidRelatedPostException.class);
    }

    @Test
    void shouldReplaceAuthorsRelatedPostsAndAttachmentsWhenUpdating() {
        Post existing = post(1L, List.of(new AttachmentRequest(AttachmentType.FILE, "https://example.com/old.pdf", null)));
        PostRequest request = new PostRequest(
                "Título novo", "Conteúdo novo", PostFormat.RST, Set.of(2L), Set.of(3L),
                List.of(new AttachmentRequest(AttachmentType.IMAGE, "https://example.com/new.png", "Capa")));
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findAllById(Set.of(2L))).thenReturn(List.of(user(2L)));
        when(postRepository.findAllById(Set.of(3L))).thenReturn(List.of(post(3L, null)));

        PostResponse response = postService.update(1L, request);

        assertThat(response.title()).isEqualTo("Título novo");
        assertThat(response.content()).isEqualTo("Conteúdo novo");
        assertThat(response.format()).isEqualTo(PostFormat.RST);
        assertThat(response.authors()).extracting(AuthorResponse::id).containsExactly(2L);
        assertThat(response.relatedPosts()).extracting(PostSummaryResponse::id).containsExactly(3L);
        assertThat(response.attachments()).extracting(AttachmentResponse::url)
                .containsExactly("https://example.com/new.png");
    }

    @Test
    void shouldRejectUpdateWhenPostDoesNotExist() {
        PostRequest request = new PostRequest("Título", "Conteúdo", null, Set.of(1L), null, null);
        when(postRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.update(1L, request))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void shouldDeletePostWhenItExists() {
        Post existing = post(1L, null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));

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

    private Post post(Long id, List<AttachmentRequest> attachments) {
        PostRequest request = new PostRequest("Post " + id, "Conteúdo", null, Set.of(1L), null, attachments);
        Post post = Post.from(request, Set.of(user(1L)), Set.of());
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .username("user" + id)
                .password("encoded")
                .email("user" + id + "@example.com")
                .role(Role.USER)
                .build();
    }
}
