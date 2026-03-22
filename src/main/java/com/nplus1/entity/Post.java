package com.nplus1.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * LAZY loading: user is NOT fetched with the post.
     * Accessing post.getUser() triggers a separate SELECT per post — N+1.
     *
     * @ManyToOne defaults to EAGER in JPA, which would load the User on every Post
     * query — even ones that never use the author. Explicitly set to LAZY here.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * LAZY loading: images are NOT fetched when a Post is loaded.
     * Accessing post.getImages() in a loop over posts triggers one
     * SELECT per post — the N+1 problem.
     *
     * List maps to a Hibernate "bag". Because User.posts is also a bag,
     * attempting to JOIN FETCH both simultaneously throws MultipleBagFetchException.
     */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Image> images = new ArrayList<>();
}
