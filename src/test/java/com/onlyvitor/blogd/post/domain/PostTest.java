package com.onlyvitor.blogd.post.domain;

import com.onlyvitor.blogd.post.dto.PostRequest;
import com.onlyvitor.blogd.user.domain.Role;
import com.onlyvitor.blogd.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostTest {

    private static final long MAIN_AUTHOR = 1L;
    private static final long COAUTHOR = 2L;
    private static final long STRANGER = 99L;
    private static final String KEY = "chave-de-teste";

    @ParameterizedTest(name = "{0} authors, acceptances by index {1} -> deletable={2}")
    @MethodSource("deletionCases")
    void shouldDeleteOnlyWithMajorityAndLetMainAuthorDecideTies(int authors, List<Integer> accepted, boolean deletable) {
        Post post = postWithAuthors(authors, PostVisibility.PUBLIC);

        accepted.forEach(index -> post.addDeletionAcceptance(user(index + MAIN_AUTHOR)));

        assertThat(post.isDeletable()).isEqualTo(deletable);
    }

    static Stream<Arguments> deletionCases() {
        return Stream.of(
                Arguments.of(1, List.of(), false),
                Arguments.of(1, List.of(0), true),
                Arguments.of(2, List.of(1), false),
                Arguments.of(2, List.of(0), true),
                Arguments.of(2, List.of(0, 1), true),
                Arguments.of(3, List.of(0), false),
                Arguments.of(3, List.of(1, 2), true),
                Arguments.of(4, List.of(1, 2), false),
                Arguments.of(4, List.of(0, 1), true),
                Arguments.of(4, List.of(1, 2, 3), true),
                Arguments.of(5, List.of(0, 1), false),
                Arguments.of(5, List.of(1, 2, 3), true)
        );
    }

    @Test
    void shouldRequireAccessKeyForPrivatePostsAndDropItForPublicPosts() {
        assertThatThrownBy(() -> postWithAuthors(1, PostVisibility.PRIVATE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Private posts need an access key");

        assertThat(postWithAuthors(1, PostVisibility.PUBLIC, KEY).getKey()).isNull();
    }

    @Test
    void shouldStoreNewKeyOnlyWhilePrivate() {
        Post post = postWithAuthors(1, PostVisibility.PRIVATE);

        post.update(request(PostVisibility.PRIVATE, Set.of()), user(MAIN_AUTHOR), Set.of(), Set.of(), "nova-chave");
        assertThat(post.getKey()).isEqualTo("nova-chave");

        post.update(request(PostVisibility.PUBLIC, Set.of()), user(MAIN_AUTHOR), Set.of(), Set.of(), null);
        assertThat(post.getKey()).isNull();
    }

    @Test
    void shouldReadPublicPostsFromAnyoneAndPrivatePostsOnlyFromAuthors() {
        Post post = postWithAuthors(2, PostVisibility.PRIVATE);

        assertThat(post.isReadableBy(MAIN_AUTHOR)).isTrue();
        assertThat(post.isReadableBy(COAUTHOR)).isTrue();
        assertThat(post.isReadableBy(STRANGER)).isFalse();

        post.update(
                request(PostVisibility.PUBLIC, Set.of(COAUTHOR)), user(MAIN_AUTHOR), coauthors(COAUTHOR), Set.of(), null);

        assertThat(post.isReadableBy(STRANGER)).isTrue();
    }

    @Test
    void shouldCountEachAuthorAcceptanceOnce() {
        Post post = postWithAuthors(2, PostVisibility.PUBLIC);

        post.addDeletionAcceptance(user(COAUTHOR));
        post.addDeletionAcceptance(user(COAUTHOR));

        assertThat(post.getDeletionAcceptances()).hasSize(1);
    }

    @Test
    void shouldRemoveAcceptanceOfAuthorWhoWithdraws() {
        Post post = postWithAuthors(2, PostVisibility.PUBLIC);
        post.addDeletionAcceptance(user(COAUTHOR));

        post.removeDeletionAcceptance(COAUTHOR);

        assertThat(post.getDeletionAcceptances()).isEmpty();
    }

    @Test
    void shouldDropAcceptancesOfAuthorsRemovedByUpdate() {
        Post post = postWithAuthors(3, PostVisibility.PUBLIC);
        post.addDeletionAcceptance(user(2L));
        post.addDeletionAcceptance(user(3L));

        post.update(request(PostVisibility.PUBLIC, Set.of(2L)), user(MAIN_AUTHOR), coauthors(2L), Set.of(), null);

        assertThat(post.getDeletionAcceptances()).extracting(User::getId).containsExactly(2L);
    }

    @Test
    void shouldPromoteEarliestCoauthorWhenMainAuthorLeaves() {
        Post post = postWithAuthors(3, PostVisibility.PUBLIC);
        post.addDeletionAcceptance(user(MAIN_AUTHOR));

        post.promoteCoauthorToAuthor();

        assertThat(post.getAuthor().getId()).isEqualTo(COAUTHOR);
        assertThat(post.getCoauthors()).extracting(User::getId).containsExactly(3L);
        assertThat(post.getDeletionAcceptances()).isEmpty();
    }

    private Post postWithAuthors(int authors, PostVisibility visibility) {
        return postWithAuthors(authors, visibility, visibility == PostVisibility.PRIVATE ? KEY : null);
    }

    private Post postWithAuthors(int authors, PostVisibility visibility, String key) {
        Set<Long> coauthorIds = LongStream.rangeClosed(COAUTHOR, authors).boxed().collect(Collectors.toSet());
        return Post.from(
                request(visibility, coauthorIds), user(MAIN_AUTHOR), coauthors(coauthorIds), Set.of(), key);
    }

    private PostRequest request(PostVisibility visibility, Set<Long> coauthorIds) {
        return new PostRequest("Título", "Conteúdo", null, visibility, MAIN_AUTHOR, coauthorIds, null, null);
    }

    private Set<User> coauthors(Long... ids) {
        return coauthors(Set.of(ids));
    }

    private Set<User> coauthors(Set<Long> ids) {
        return ids.stream().map(this::user).collect(Collectors.toSet());
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
