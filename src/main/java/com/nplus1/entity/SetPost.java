package com.nplus1.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Read-only view of the posts table using a Set-based @OneToMany collection.
 *
 * Unlike {@link Post}, which uses List (Hibernate "bag"), this entity uses Set.
 * Combined with {@link SetUser}, both collections can be fetched in a single
 * @EntityGraph query without causing MultipleBagFetchException.
 */
@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor
public class SetPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private SetUser user;

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    private Set<SetImage> images = new LinkedHashSet<>();
}
