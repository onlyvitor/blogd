package com.onlyvitor.blogd.post.repository;

import com.onlyvitor.blogd.post.domain.Post;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Override
    @EntityGraph(attributePaths = {"authors", "relatedPosts", "attachments"})
    List<Post> findAll();

    @Override
    @EntityGraph(attributePaths = {"authors", "relatedPosts", "attachments"})
    Optional<Post> findById(Long id);

    @Query("select a.id from Post p join p.authors a where p.id = :postId")
    List<Long> findAuthorIdsByPostId(@Param("postId") Long postId);
}
