package com.onlyvitor.blogd.post.security;

import com.onlyvitor.blogd.post.repository.PostRepository;
import com.onlyvitor.blogd.user.security.UserSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component("postSecurity")
@RequiredArgsConstructor
public class PostSecurity {

    private final PostRepository postRepository;
    private final UserSecurity userSecurity;

    public boolean isAuthor(Authentication authentication, Long postId) {
        return postRepository.findAuthorIdsByPostId(postId).stream()
                .anyMatch(authorId -> userSecurity.isSelf(authentication, authorId));
    }

    public boolean includesSelf(Authentication authentication, Set<Long> authorIds) {
        return authorIds.stream()
                .anyMatch(authorId -> userSecurity.isSelf(authentication, authorId));
    }
}
