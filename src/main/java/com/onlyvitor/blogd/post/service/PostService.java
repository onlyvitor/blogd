package com.onlyvitor.blogd.post.service;

import com.onlyvitor.blogd.post.domain.Post;
import com.onlyvitor.blogd.post.dto.PostRequest;
import com.onlyvitor.blogd.post.dto.PostResponse;
import com.onlyvitor.blogd.post.exception.InvalidRelatedPostException;
import com.onlyvitor.blogd.post.exception.PostNotFoundException;
import com.onlyvitor.blogd.post.repository.PostRepository;
import com.onlyvitor.blogd.user.domain.User;
import com.onlyvitor.blogd.user.exception.UserNotFoundException;
import com.onlyvitor.blogd.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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

    public List<PostResponse> listAll() {
        return postRepository.findAll().stream()
                .map(PostResponse::from)
                .toList();
    }

    public PostResponse findById(Long id) {
        return PostResponse.from(findEntity(id));
    }

    @Transactional
    public PostResponse create(PostRequest request) {
        Set<User> authors = findAuthors(request.authorIds());
        Set<Post> relatedPosts = findRelatedPosts(request.relatedPostIds(), null);
        Post saved = postRepository.save(Post.from(request, authors, relatedPosts));
        return PostResponse.from(saved);
    }

    @Transactional
    public PostResponse update(Long id, PostRequest request) {
        Post post = findEntity(id);
        Set<User> authors = findAuthors(request.authorIds());
        Set<Post> relatedPosts = findRelatedPosts(request.relatedPostIds(), id);
        post.update(request, authors, relatedPosts);
        return PostResponse.from(post);
    }

    @Transactional
    public void delete(Long id) {
        postRepository.delete(findEntity(id));
    }

    private Post findEntity(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
    }

    private Set<User> findAuthors(Set<Long> authorIds) {
        List<User> authors = userRepository.findAllById(authorIds);
        Set<Long> foundIds = authors.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        for (Long authorId : authorIds) {
            if (!foundIds.contains(authorId)) {
                throw new UserNotFoundException(authorId);
            }
        }
        return new LinkedHashSet<>(authors);
    }

    private Set<Post> findRelatedPosts(Set<Long> relatedPostIds, Long postId) {
        if (postId != null && relatedPostIds.contains(postId)) {
            throw new InvalidRelatedPostException();
        }

        List<Post> relatedPosts = postRepository.findAllById(relatedPostIds);
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
