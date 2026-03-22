package com.nplus1.controller;

import com.nplus1.dto.ImageDto;
import com.nplus1.dto.PostDto;
import com.nplus1.dto.UserDto;
import com.nplus1.repository.SetUserRepository;
import com.nplus1.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bag-problem")
@RequiredArgsConstructor
@Slf4j
public class BagProblemController {

    private final UserRepository userRepository;
    private final SetUserRepository setUserRepository;

    /**
     * ❌ CRASH — MultipleBagFetchException
     *
     * The repository method uses:
     *
     *   @EntityGraph(attributePaths = {"posts", "posts.images"})
     *
     * User.posts  → List<Post>   (bag #1)
     * Post.images → List<Image>  (bag #2)
     *
     * Hibernate refuses: fetching two unbounded bags in one query creates a
     * Cartesian product that cannot be safely deduplicated.
     *
     * Returns HTTP 422 with the real exception message.
     *
     * GET /api/bag-problem/crash
     */
    @GetMapping("/crash")
    public Map<String, Object> crash() {
        log.warn("Fetching users with posts and images using two concurrent List bags");
        try {
            userRepository.findAllWithMultipleBags();
            return Map.of("result", "unexpectedly succeeded");
        } catch (InvalidDataAccessApiUsageException ex) {
            Throwable root = getRootCause(ex);
            log.error("MultipleBagFetchException caught: {}", root.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "MultipleBagFetchException: " + root.getMessage() +
                    " | FIX: change List to Set on @OneToMany collections, " +
                    "then call GET /api/bag-problem/fix"
            );
        }
    }

    /**
     * Demonstrates the one-bag-at-a-time workaround: load only one @OneToMany
     * collection per @EntityGraph call. A single List collection never triggers
     * MultipleBagFetchException — the exception only occurs when two or more
     * List-based @OneToMany associations are fetched simultaneously.
     *
     * For a query that fetches ALL nested collections at once without crashing,
     * see GET /api/bag-problem/fix-with-set.
     *
     * GET /api/bag-problem/fix
     */
    @GetMapping("/fix")
    public List<UserDto> fix() {
        log.info("@EntityGraph: fetching users with posts using a single bag");
        return userRepository.findAllWithPostsEntityGraph()
                .stream()
                .map(u -> new UserDto(
                        u.getId(),
                        u.getFullName(),
                        u.getEmail(),
                        u.getPosts().stream().map(p -> new PostDto(
                                p.getId(),
                                p.getTitle(),
                                p.getContent(),
                                List.of()
                        )).toList(),
                        List.of()
                ))
                .toList();
    }

    /**
     * Demonstrates the Set-based fix for MultipleBagFetchException.
     *
     * SetUser.posts  → Set<SetPost>   (not a bag — safe to multi-fetch)
     * SetPost.images → Set<SetImage>  (not a bag — safe to multi-fetch)
     *
     * @EntityGraph({"posts", "posts.images"}) now works without crashing:
     * Hibernate deduplicates the Cartesian product rows using Set semantics.
     * Watch the console — a single SELECT with two JOINs, no extra queries.
     *
     * GET /api/bag-problem/fix-with-set
     */
    @GetMapping("/fix-with-set")
    public List<UserDto> fixWithSet() {
        log.info("@EntityGraph with Set: fetching users, posts, and images in a single query");
        return setUserRepository.findAllWithPostsAndImages()
                .stream()
                .map(u -> new UserDto(
                        u.getId(),
                        u.getFullName(),
                        u.getEmail(),
                        u.getPosts().stream().map(p -> new PostDto(
                                p.getId(),
                                p.getTitle(),
                                p.getContent(),
                                p.getImages().stream()
                                        .map(i -> new ImageDto(i.getId(), i.getUrl()))
                                        .toList()
                        )).toList(),
                        u.getImages().stream()
                                .map(i -> new ImageDto(i.getId(), i.getUrl()))
                                .toList()
                ))
                .toList();
    }

    private Throwable getRootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}

