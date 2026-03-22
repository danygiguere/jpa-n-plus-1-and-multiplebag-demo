package com.nplus1.controller;

import com.nplus1.dto.ImageDto;
import com.nplus1.dto.PostDto;
import com.nplus1.entity.Post;
import com.nplus1.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostRepository postRepository;

    /**
     * ❌ N+1 PROBLEM — two levels
     *
     * Hibernate fires:
     *   1 SELECT to load all posts
     *   + N SELECTs to load images for each post   (N+1 on images)
     *   + N SELECTs to load user for each post     (N+1 on user)
     *
     * With 12 posts → 1 + 12 + 12 = 25 queries.
     *
     * GET /api/posts/n-plus-one
     */
    @GetMapping("/n-plus-one")
    public List<PostDto> nPlusOne() {
        log.warn("N+1: loading posts then accessing images and author lazily");
        List<Post> posts = postRepository.findAll();

        return posts.stream().map(p -> new PostDto(
                p.getId(),
                "[by: " + p.getUser().getFullName() + "] " + p.getTitle(),
                p.getContent(),
                p.getImages().stream().map(i -> new ImageDto(i.getId(), i.getUrl())).toList()
        )).toList();
    }

    /**
     * Loads posts and images in a single JOIN query via JPQL JOIN FETCH.
     * Eliminates the N+1 on images — watch the console for a single SELECT with a JOIN.
     *
     * GET /api/posts/fetch-join
     */
    @GetMapping("/fetch-join")
    public List<PostDto> fetchJoin() {
        log.info("JOIN FETCH: loading posts and images in one query");
        List<Post> posts = postRepository.findAllWithImagesFetchJoin();

        return posts.stream().map(p -> new PostDto(
                p.getId(),
                p.getTitle(),
                p.getContent(),
                p.getImages().stream().map(i -> new ImageDto(i.getId(), i.getUrl())).toList()
        )).toList();
    }

    /**
     * Loads posts, their author, and their images in one query using @EntityGraph.
     * Post.images is a single bag — safe to combine with a ManyToOne in one fetch.
     *
     * GET /api/posts/entity-graph
     */
    @GetMapping("/entity-graph")
    public List<PostDto> entityGraph() {
        log.info("@EntityGraph: loading posts, author, and images in one query");
        List<Post> posts = postRepository.findAllWithUserAndImagesEntityGraph();

        return posts.stream().map(p -> new PostDto(
                p.getId(),
                "[by: " + p.getUser().getFullName() + "] " + p.getTitle(),
                p.getContent(),
                p.getImages().stream().map(i -> new ImageDto(i.getId(), i.getUrl())).toList()
        )).toList();
    }
}
