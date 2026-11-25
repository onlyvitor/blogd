package com.onlyvitor.blogd.post.service;

import com.onlyvitor.blogd.post.domain.Post;
import com.onlyvitor.blogd.post.dto.PostRequest;
import com.onlyvitor.blogd.post.dto.PostResponse;
import com.onlyvitor.blogd.post.exception.InvalidRelatedPostException;
import com.onlyvitor.blogd.post.exception.PostDeletionNotAllowedException;
import com.onlyvitor.blogd.post.exception.PostNotFoundException;
import com.onlyvitor.blogd.post.repository.PostRepository;
import com.onlyvitor.blogd.user.domain.User;
import com.onlyvitor.blogd.user.exception.UserNotFoundException;
import com.onlyvitor.blogd.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostKeyManager postKeyManager;

    public List<PostResponse> listVisibleTo(long userId) {
        return postRepository.findAllVisibleTo(userId).stream()
                .map(post -> PostResponse.from(post, userId))
                .toList();
    }

    public PostResponse findById(Long id, long requesterId, String key) {
        Post post = findEntity(id);
        if (!post.isReadableBy(requesterId) && !postKeyManager.matches(post.getKey(), key)) {
            throw new PostNotFoundException(id);
        }
        return PostResponse.from(post, requesterId);
    }

    @Transactional
    public PostResponse create(PostRequest request, long requesterId) {
        User author = findUser(request.authorId());
        Set<User> coauthors = findUsers(request.coauthorIds());
        Set<Post> relatedPosts = findRelatedPosts(request.relatedPostIds(), null, requesterId);
        String key = postKeyManager.resolveKey(request.visibility(), null);
        Post saved = postRepository.save(Post.from(request, author, coauthors, relatedPosts, key));
        return PostResponse.from(saved, requesterId);
    }

    @Transactional
    public PostResponse update(Long id, PostRequest request, long requesterId) {
        Post post = findEntity(id);
        if (!post.hasSameAuthors(request.authorId(), request.coauthorIds()) && !post.isMainAuthor(requesterId)) {
            throw new AccessDeniedException("Only the main author can change the authors of a post");
        }
        User author = findUser(request.authorId());
        Set<User> coauthors = findUsers(request.coauthorIds());
        Set<Post> relatedPosts = findRelatedPosts(request.relatedPostIds(), id, requesterId);
        String key = postKeyManager.resolveKey(request.visibility(), post.getKey());
        post.update(request, author, coauthors, relatedPosts, key);
        return PostResponse.from(post, requesterId);
    }

    @Transactional
    public void delete(Long id) {
        Post post = findEntity(id);
        if (!post.isDeletable()) {
            throw new PostDeletionNotAllowedException();
        }
        postRepository.delete(post);
    }

    @Transactional
    public PostResponse acceptDeletion(Long id, long requesterId) {
        Post post = findEntity(id);
        post.addDeletionAcceptance(findAuthor(post, requesterId));
        return PostResponse.from(post, requesterId);
    }

    @Transactional
    public PostResponse withdrawDeletionAcceptance(Long id, long requesterId) {
        Post post = findEntity(id);
        post.removeDeletionAcceptance(requesterId);
        return PostResponse.from(post, requesterId);
    }

    @Transactional
    public void handleAuthorDeletion(long userId) {
        for (Post post : postRepository.findAllByAuthorId(userId)) {
            if (post.getCoauthors().isEmpty()) {
                postRepository.delete(post);
            } else {
                post.promoteCoauthorToAuthor();
            }
        }
        postRepository.flush();
    }

    private Post findEntity(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
    }

    private User findAuthor(Post post, long userId) {
        if (post.isMainAuthor(userId)) {
            return post.getAuthor();
        }
        return post.getCoauthors().stream()
                .filter(coauthor -> coauthor.getId() == userId)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Only authors can accept the deletion of a post"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private Set<User> findUsers(Set<Long> userIds) {
        List<User> users = userRepository.findAllById(userIds);
        Set<Long> foundIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        for (Long userId : userIds) {
            if (!foundIds.contains(userId)) {
                throw new UserNotFoundException(userId);
            }
        }
        return new LinkedHashSet<>(users);
    }

    private Set<Post> findRelatedPosts(Set<Long> relatedPostIds, Long postId, long requesterId) {
        if (postId != null && relatedPostIds.contains(postId)) {
            throw new InvalidRelatedPostException();
        }

        List<Post> relatedPosts = postRepository.findAllById(relatedPostIds).stream()
                .filter(post -> post.isReadableBy(requesterId))
                .toList();
        Set<Long> foundIds = relatedPosts.stream()
                .map(Post::getId)
                .collect(Collectors.toSet());

        for (Long relatedPostId : relatedPostIds) {
            if (!foundIds.contains(relatedPostId)) {
                throw new PostNotFoundException(relatedPostId);
            }
        }
        return new LinkedHashSet<>(relatedPosts);
    }
}
