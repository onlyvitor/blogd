package com.onlyvitor.blogd.post.controller;

import com.onlyvitor.blogd.post.dto.PostRequest;
import com.onlyvitor.blogd.post.dto.PostResponse;
import com.onlyvitor.blogd.post.service.PostService;
import com.onlyvitor.blogd.user.security.UserSecurity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private static final String POST_KEY_HEADER = "X-Post-Key";

    private final PostService postService;
    private final UserSecurity userSecurity;

    @GetMapping
    public ResponseEntity<List<PostResponse>> listVisible(Authentication authentication) {
        return ResponseEntity.ok(postService.listVisibleTo(requesterId(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> findById(
            @PathVariable Long id,
            @RequestHeader(value = POST_KEY_HEADER, required = false) String key,
            Authentication authentication) {
        return ResponseEntity.ok(postService.findById(id, requesterId(authentication), key));
    }

    @PostMapping
    @PreAuthorize("@userSecurity.isSelf(authentication, #request.authorId())")
    public ResponseEntity<PostResponse> create(
            @Valid @RequestBody PostRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.create(request, requesterId(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@postSecurity.isAuthor(authentication, #id)"
            + " and @postSecurity.includesSelf(authentication, #request.authorId(), #request.coauthorIds())")
    public ResponseEntity<PostResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PostRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(postService.update(id, request, requesterId(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@postSecurity.isAuthor(authentication, #id)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/deletion-acceptance")
    @PreAuthorize("@postSecurity.isAuthor(authentication, #id)")
    public ResponseEntity<PostResponse> acceptDeletion(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(postService.acceptDeletion(id, requesterId(authentication)));
    }

    @DeleteMapping("/{id}/deletion-acceptance")
    @PreAuthorize("@postSecurity.isAuthor(authentication, #id)")
    public ResponseEntity<PostResponse> withdrawDeletionAcceptance(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(postService.withdrawDeletionAcceptance(id, requesterId(authentication)));
    }

    private long requesterId(Authentication authentication) {
        return userSecurity.currentUserId(authentication)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user id is not available"));
    }
}
