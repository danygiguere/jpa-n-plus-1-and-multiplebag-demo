package com.nplus1.repository;

import com.nplus1.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * N+1 scenario: plain findAll() loads only users.
     * Each subsequent call to user.getPosts() or user.getImages()
     * fires an additional SELECT — 1 + N queries total.
     */
    // (inherited) List<User> findAll()

    /**
     * Bag problem scenario: attempts to JOIN FETCH two List (bag) collections
     * in a single query.
     *
     * User.posts  → List<Post>   (bag #1)
     * Post.images → List<Image>  (bag #2)
     *
     * Hibernate throws MultipleBagFetchException because fetching two unbounded
     * bags produces a Cartesian product it cannot safely deduplicate.
     * Fix: change List → Set on @OneToMany collections.
     */
    @EntityGraph(attributePaths = {"posts", "posts.images"})
    @Query("SELECT DISTINCT u FROM User u")
    List<User> findAllWithMultipleBags();

    /**
     * Loads users and posts in one query using JPQL JOIN FETCH.
     * One bag at a time — no MultipleBagFetchException risk.
     */
    @Query("SELECT DISTINCT u FROM User u JOIN FETCH u.posts")
    List<User> findAllWithPostsFetchJoin();

    /**
     * Loads users and posts in one query using @EntityGraph.
     * Equivalent to JOIN FETCH but without hand-written JPQL.
     * One bag at a time — no MultipleBagFetchException risk.
     */
    @EntityGraph(attributePaths = {"posts"})
    @Query("SELECT DISTINCT u FROM User u")
    List<User> findAllWithPostsEntityGraph();

    /**
     * Loads users and their profile images in one query using JPQL JOIN FETCH.
     */
    @Query("SELECT DISTINCT u FROM User u JOIN FETCH u.images")
    List<User> findAllWithImagesFetchJoin();
}
