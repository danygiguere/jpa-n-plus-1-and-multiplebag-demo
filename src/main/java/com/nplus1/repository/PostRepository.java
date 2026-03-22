package com.nplus1.repository;

import com.nplus1.entity.Post;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * N+1 scenario: plain findAll() loads only posts.
     * Accessing post.getImages() or post.getUser() in a loop each fire
     * an additional SELECT per post — 1 + N + N queries total.
     */
    // (inherited) List<Post> findAll()

    /**
     * Loads posts and images in one query using JPQL JOIN FETCH.
     */
    @Query("SELECT DISTINCT p FROM Post p JOIN FETCH p.images")
    List<Post> findAllWithImagesFetchJoin();

    /**
     * Loads posts and their author in one query using JPQL JOIN FETCH.
     * Resolves the ManyToOne N+1 on post.getUser().
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.user")
    List<Post> findAllWithUserFetchJoin();

    /**
     * Loads posts, their author, and their images in one query using @EntityGraph.
     * Post.images is a single bag — safe to fetch alongside a ManyToOne.
     */
    @EntityGraph(attributePaths = {"user", "images"})
    @Query("SELECT DISTINCT p FROM Post p")
    List<Post> findAllWithUserAndImagesEntityGraph();
}
