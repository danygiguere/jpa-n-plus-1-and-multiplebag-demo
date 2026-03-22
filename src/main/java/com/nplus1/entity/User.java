package com.nplus1.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    /**
     * LAZY loading: posts are NOT fetched when a User is loaded.
     * Accessing user.getPosts() outside a transaction triggers a separate
     * SELECT per user — the classic N+1 problem.
     *
     * List maps to a Hibernate "bag": unordered and allowing duplicates
     * in result sets. Fetching two bags simultaneously in one query
     * produces a Cartesian product Hibernate cannot safely deduplicate,
     * causing MultipleBagFetchException.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Post> posts = new ArrayList<>();

    /**
     * LAZY loading: accessing user.getImages() outside a transaction
     * triggers a separate SELECT per user — same N+1 risk as posts.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Image> images = new ArrayList<>();
}
