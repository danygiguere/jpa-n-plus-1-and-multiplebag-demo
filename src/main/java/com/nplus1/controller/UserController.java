package com.nplus1.controller;

import com.nplus1.dto.ImageDto;
import com.nplus1.dto.PostDto;
import com.nplus1.dto.UserDto;
import com.nplus1.entity.User;
import com.nplus1.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserRepository userRepository;

    /**
     * ❌ N+1 PROBLEM
     *
     * Hibernate fires:
     *   1 SELECT to load all users
     *   + N SELECTs to load posts for each user
     *   + N SELECTs to load images for each user
     *
     * With 3 users → 1 + 3 + 3 = 7 queries.
     * Watch the console for repeated "select ... from posts where user_id=?" queries.
     *
     * GET /api/users/n-plus-one
     */
    @GetMapping("/n-plus-one")
    public List<UserDto> nPlusOne() {
        log.warn("N+1: loading users then accessing posts and images lazily");
        List<User> users = userRepository.findAll();

        return users.stream().map(u -> new UserDto(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getPosts().stream().map(p -> new PostDto(
                        p.getId(), p.getTitle(), p.getContent(), List.of()
                )).toList(),
                u.getImages().stream().map(i -> new ImageDto(i.getId(), i.getUrl())).toList()
        )).toList();
    }

    /**
     * Loads users and posts in a single JOIN query via JPQL JOIN FETCH.
     * Eliminates the N+1 on posts — watch the console for a single SELECT with a JOIN.
     *
     * GET /api/users/fetch-join
     */
    @GetMapping("/fetch-join")
    public List<UserDto> fetchJoin() {
        log.info("JOIN FETCH: loading users and posts in one query");
        List<User> users = userRepository.findAllWithPostsFetchJoin();

        return users.stream().map(u -> new UserDto(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getPosts().stream().map(p -> new PostDto(
                        p.getId(), p.getTitle(), p.getContent(), List.of()
                )).toList(),
                List.of()
        )).toList();
    }

    /**
     * Loads users and posts using @EntityGraph — same result as JOIN FETCH
     * but without hand-written JPQL.
     *
     * With List-based collections, @EntityGraph is safe for one collection at a time.
     * Fetching two List collections simultaneously causes MultipleBagFetchException
     * — demonstrated at GET /api/bag-problem/crash.
     *
     * GET /api/users/entity-graph
     */
    @GetMapping("/entity-graph")
    public List<UserDto> entityGraph() {
        log.info("@EntityGraph: loading users and posts in one query");
        List<User> users = userRepository.findAllWithPostsEntityGraph();

        return users.stream().map(u -> new UserDto(
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
        )).toList();
    }

    /**
     * Loads users and their profile images in a single JOIN query.
     *
     * GET /api/users/fetch-join-images
     */
    @GetMapping("/fetch-join-images")
    public List<UserDto> fetchJoinImages() {
        log.info("JOIN FETCH: loading users and profile images in one query");
        List<User> users = userRepository.findAllWithImagesFetchJoin();

        return users.stream().map(u -> new UserDto(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                List.of(),
                u.getImages().stream().map(i -> new ImageDto(i.getId(), i.getUrl())).toList()
        )).toList();
    }
}
