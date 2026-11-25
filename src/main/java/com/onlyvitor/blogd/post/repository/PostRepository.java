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
    @EntityGraph(attributePaths = {"author", "coauthors", "deletionAcceptances", "relatedPosts", "attachments"})
    Optional<Post> findById(Long id);

    @EntityGraph(attributePaths = {"author", "coauthors", "deletionAcceptances", "relatedPosts", "attachments"})
    @Query("""
            select distinct p from Post p
            left join p.coauthors c
            where p.visibility = com.onlyvitor.blogd.post.domain.PostVisibility.PUBLIC
               or p.author.id = :userId
               or c.id = :userId
            """)
    List<Post> findAllVisibleTo(@Param("userId") Long userId);

    @Query("select p.author.id from Post p where p.id = :postId")
    Optional<Long> findAuthorIdByPostId(@Param("postId") Long postId);

    @Query("select c.id from Post p join p.coauthors c where p.id = :postId")
    List<Long> findCoauthorIdsByPostId(@Param("postId") Long postId);

    List<Post> findAllByAuthorId(Long authorId);
}
