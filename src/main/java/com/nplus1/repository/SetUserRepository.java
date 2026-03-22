package com.nplus1.repository;

import com.nplus1.entity.SetUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SetUserRepository extends JpaRepository<SetUser, Long> {

    /**
     * Fetches users, their posts, and each post's images in a single query
     * using @EntityGraph across two Set-based @OneToMany collections.
     *
     * SetUser.posts  → Set<SetPost>   (not a bag — safe to multi-fetch)
     * SetPost.images → Set<SetImage>  (not a bag — safe to multi-fetch)
     *
     * Hibernate deduplicates the JOIN result rows using Set.equals/hashCode,
     * so no MultipleBagFetchException is thrown.
     *
     * Compare with {@link UserRepository#findAllWithMultipleBags()}, which
     * attempts the same @EntityGraph on List-based collections and crashes.
     */
    @EntityGraph(attributePaths = {"posts", "posts.images"})
    @Query("SELECT DISTINCT u FROM SetUser u")
    List<SetUser> findAllWithPostsAndImages();
}
