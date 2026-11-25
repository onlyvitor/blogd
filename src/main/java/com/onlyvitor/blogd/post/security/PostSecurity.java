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
        boolean isMainAuthor = postRepository.findAuthorIdByPostId(postId)
                .map(authorId -> userSecurity.isSelf(authentication, authorId))
                .orElse(false);
        return isMainAuthor || postRepository.findCoauthorIdsByPostId(postId).stream()
                .anyMatch(coauthorId -> userSecurity.isSelf(authentication, coauthorId));
    }

    public boolean includesSelf(Authentication authentication, Long authorId, Set<Long> coauthorIds) {
        return userSecurity.isSelf(authentication, authorId) || coauthorIds.stream()
                .anyMatch(coauthorId -> userSecurity.isSelf(authentication, coauthorId));
    }
}
