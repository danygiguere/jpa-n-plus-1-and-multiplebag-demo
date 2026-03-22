package com.nplus1.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Read-only view of the users table using Set-based @OneToMany collections.
 *
 * Unlike {@link User}, which uses List (Hibernate "bag"), this entity uses Set.
 * Hibernate can safely deduplicate JOIN result rows via Set semantics, which
 * allows @EntityGraph to fetch multiple nested collections simultaneously
 * without throwing MultipleBagFetchException.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class SetUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<SetPost> posts = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<SetImage> images = new LinkedHashSet<>();
}
