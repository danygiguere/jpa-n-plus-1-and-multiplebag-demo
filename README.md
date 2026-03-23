# Spring Boot — JPA N+1 & MultipleBag Demo

🇬🇧 English | 🇫🇷 [Français](README.fr.md)

This project demonstrates two of the most common and misunderstood query problems in
Hibernate/JPA: the **N+1 problem** and the **MultipleBag problem**. Both are silent by
default, both are easy to ship under time pressure, and both have well-understood fixes.

The app is a Spring Boot 3 REST API with SQL logging enabled. Every endpoint is designed
to show a specific behaviour in the console — the broken version first, the fix right
beside it. Import the Postman collection, start the app, and watch the query counts.

---

## Table of Contents

- [The N+1 Problem](#the-n1-problem)
- [Why It Matters](#why-it-matters)
- [Getting Started](#getting-started)
- [Endpoints](#endpoints)
  - [Users](#users)
  - [Posts](#posts)
  - [Bag Problem](#bag-problem)
- [JPA Fetch Type Defaults](#jpa-fetch-type-defaults)
- [N+1: The Two Fixes](#n1-the-two-fixes)
  - [Fix 1 — JPQL JOIN FETCH](#fix-1--jpql-join-fetch)
  - [Fix 2 — @EntityGraph](#fix-2--entitygraph)
- [The EAGER Trap](#the-eager-trap)
- [The MultipleBag Problem](#the-multiplebag-problem)
- [JPA Relationships: Architectural Considerations](#jpa-relationships-architectural-considerations)
- [The N+1 Problem is Not JPA-Specific](#the-n1-problem-is-not-jpa-specific)
- [N+1 Without an ORM](#n1-without-an-orm)

---

## The N+1 Problem

The classic case is with `LAZY` loading: associations are not fetched immediately —
they are proxied and loaded on demand the first time you access them. The problem
arises when you access that association inside a loop: Hibernate fires one `SELECT`
per row rather than fetching everything upfront.

```java
List<User> users = userRepository.findAll(); // 1 query

for (User user : users) {
    user.getPosts(); // 1 query per user — silent, no warning
}
```

With 3 users that is 4 queries. With 100 it is 101. The formula — **1 + N** — is where
the name comes from.

N+1 is not exclusive to `LAZY` loading, however. `EAGER` associations can produce the
same pattern — Hibernate fires the extra selects automatically on every load, without
any explicit loop in your code. The difference is that `LAZY` makes the trigger
visible (the getter call), while `EAGER` hides it entirely inside Hibernate's
bootstrapping. Either way, the fix is the same: `JOIN FETCH` or `@EntityGraph`.

What makes this problem particularly insidious is that the code looks completely normal.
There is no exception, no log message, no indication that anything is wrong. The extra
queries only become visible when you enable SQL logging, which most developers do not
have on in production and often forget to check in development.

---

## Why It Matters

N+1 is deceptively cheap in a development environment with seed data. It becomes
expensive fast in production, where N is large and associations are often nested.

| Scenario | Entities | Associations accessed | Total queries |
|---|---|---|---|
| This demo — users | 3 users | posts + images | **7** (1 + 3 + 3) |
| This demo — posts | 12 posts | images + author | **25** (1 + 12 + 12) |
| Social feed (20 posts/page) | 20 posts | author + likes + comments + media | **81** |
| Blog post list (paginated, 50 posts) | 50 posts | author + tags + comments | **151** |
| E-commerce order list | 100 orders | items + customer + shipping | **301** |
| Admin user export (500 users) | 500 users | roles + last login + profile | **1 501** |

A single request that loads 100 orders and touches three associations produces 301
queries. The endpoint looks fine in testing, ships to production, and becomes a database
bottleneck weeks later when traffic picks up. By that point the association access is
spread across multiple service methods and is not trivial to trace back.

---

## Getting Started

**Requirements:** Java 21+, Gradle (wrapper included)

```bash
./gradlew bootRun
```

The app starts at `http://localhost:8080`. Every SQL query Hibernate fires is printed
to the console — this is intentional. Watch the query count change as you call the
N+1 endpoints versus the fix endpoints.

**Data model** — three entities, seeded automatically on startup:

```
User
 ├── posts  (List<Post>)  — one-to-many, LAZY
 │    └── images  (List<Image>)  — one-to-many, LAZY
 └── images  (List<Image>)  — one-to-many, LAZY  (profile pictures)
```

| Entity | Count |
|---|---|
| Users | 3 |
| Posts per user | 4 → **12 total** |
| Images per post | 3 → **36 total** |
| Profile images per user | 2 → **6 total** |

**H2 Console** — data is stored in-memory and re-seeded on every restart.

```
URL:       http://localhost:8080/h2-console
JDBC URL:  jdbc:h2:mem:n1db
Username:  sa
Password:  (empty)
```

**Key configuration** (`application.properties`):

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
server.error.include-message=always
```

---

## Endpoints

Import `jpa-n1-demo.postman_collection.json` into Postman (`{{baseUrl}}` = `http://localhost:8080`).

### Users

| Endpoint | What to observe |
|---|---|
| `GET /api/users/n-plus-one` | Console shows **7 queries**: 1 for users + 3 for posts + 3 for images |
| `GET /api/users/fetch-join` | Console shows **1 query** with a `JOIN` — posts loaded alongside users |
| `GET /api/users/entity-graph` | Console shows **1 query** via `@EntityGraph` — same result, no JPQL needed |
| `GET /api/users/fetch-join-images` | Console shows **1 query** — profile images loaded alongside users |

### Posts

| Endpoint | What to observe |
|---|---|
| `GET /api/posts/n-plus-one` | Console shows **25 queries**: 1 for posts + 12 for images + 12 for authors |
| `GET /api/posts/fetch-join` | Console shows **1 query** — images loaded alongside posts |
| `GET /api/posts/entity-graph` | Console shows **1 query** — author and images both loaded at once |

### Bag Problem

| Endpoint | What to observe |
|---|---|
| `GET /api/bag-problem/crash` | Returns **HTTP 422** with the real `MultipleBagFetchException` message |
| `GET /api/bag-problem/fix` | Returns **HTTP 200** — fetching one bag at a time is always safe |
| `GET /api/bag-problem/fix-with-set` | Returns **HTTP 200** — Set-based entities, both collections in one query |

---

## JPA Fetch Type Defaults

Every JPA association has a fetch type that controls when Hibernate loads the related
data. When you write an annotation with no `fetch =` argument, JPA silently applies
its own defaults:

| Association | Default | Risk |
|---|---|---|
| `@OneToMany()` | **LAZY** | Safe — data is only loaded when you access the collection |
| `@ManyToMany()` | **LAZY** | Safe |
| `@ManyToOne()` | **EAGER** ⚠️ | Every query on `Post` also loads its `User`, always |
| `@OneToOne()` | **EAGER** ⚠️ | Every query on the owning side also loads the other side |

**LAZY** means Hibernate does not fetch the association until you access it. The
related data stays unloaded — represented by a proxy object — until the getter is
called, at which point Hibernate fires a `SELECT` on the spot. This is efficient when
you do not need the data. It is the root cause of N+1 when you call that getter inside
a loop.

**EAGER** means Hibernate loads the association on every query, automatically, whether
you use the result or not. A simple count query, a lightweight search endpoint, a
projection that only needs two fields — all of them silently pay the full JOIN cost.

The two dangerous defaults — `@ManyToOne` and `@OneToOne` — mean that without any
explicit configuration, querying `Post` will always also load its `User`. This is
rarely what you want in every single context. It is why all associations in this
project are explicitly declared `LAZY`, overriding the JPA defaults:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
private User user;
```

Understanding these defaults makes both the [fixes below](#the-two-fixes) and the
[EAGER trap](#the-eager-trap) easier to reason about.

---

## N+1: The Two Fixes

The N+1 problem has two standard solutions in JPA. Both work by telling Hibernate to
load the association with a `JOIN` at the time of the query, rather than lazily per row.

### Fix 1 — JPQL `JOIN FETCH`

```java
@Query("SELECT DISTINCT u FROM User u JOIN FETCH u.posts")
List<User> findAllWithPostsFetchJoin();
```

Hibernate rewrites this into a single SQL `JOIN` query. The `DISTINCT` prevents
duplicate `User` objects when a user has multiple posts. Full control, but you write
the JPQL yourself.

### Fix 2 — `@EntityGraph`

```java
@EntityGraph(attributePaths = {"posts"})
@Query("SELECT DISTINCT u FROM User u")
List<User> findAllWithPostsEntityGraph();
```

Declares the eager fetch as metadata on the repository method rather than in the query
string. Hibernate generates the same `JOIN` under the hood. Useful when you want to
keep the JPQL clean or reuse a base query across multiple fetch strategies.

Both approaches produce one query instead of N+1. The choice between them is mostly
a matter of style and how complex your queries are.

---

## The EAGER Trap

When developers first encounter N+1, a common instinct is to set `FetchType.EAGER` on
the association. This looks like a fix. It is not.

```java
// ❌ This does not fix N+1 — it hides it and makes it worse
@OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
private List<Post> posts;
```

With `EAGER`, Hibernate loads the association automatically on every query — including
queries where you never use the association. A simple `userRepository.findAll()` call,
a count query, a search endpoint that only needs the user's email address — all of them
now silently load all posts for every user.

```
SELECT * FROM users
SELECT * FROM posts WHERE user_id = 1
SELECT * FROM posts WHERE user_id = 2
SELECT * FROM posts WHERE user_id = 3
```

The N+1 is now baked into the entity itself and cannot be turned off without writing
custom JPQL. Every query pays the full cost whether it needs the data or not.

It also compounds the defaults problem. As covered in the
[JPA Fetch Type Defaults](#jpa-fetch-type-defaults) section, `@ManyToOne` is already
`EAGER` without any annotation — meaning every post query silently loads its author
even before you touch `FetchType.EAGER` yourself. Setting `EAGER` explicitly on a
`@OneToMany` stacks on top of that.

`EAGER` is only appropriate when all three of these are true: 100% of the queries using
this entity need the associated data, the collection is small and bounded, and you are
fully aware of the `JOIN` that gets added to every query. In most cases, none of those
conditions hold.

> **Rule of thumb:** always start with `LAZY`. Fetch associations explicitly with
> `JOIN FETCH` or `@EntityGraph`, only on the queries that actually need them.

---

## The MultipleBag Problem

Once you understand N+1 and reach for `@EntityGraph` to fix it, you may run into a
second problem: `MultipleBagFetchException`.

Hibernate uses the term **bag** for a `java.util.List` on a `@OneToMany` association —
an unordered collection that permits duplicates in result sets. When you attempt to
fetch two bag collections simultaneously with a single `@EntityGraph` or `JOIN FETCH`,
Hibernate throws an exception and refuses to run:

```
org.hibernate.loader.MultipleBagFetchException:
  cannot simultaneously fetch multiple bags: [User.posts, Post.images]
```

The reason is mathematical. A `JOIN` on one collection multiplies the rows in the
result set — one row per combination of parent and child. A second `JOIN` multiplies
again. Two bags produce an unbounded Cartesian product that Hibernate cannot safely
deduplicate back into distinct entity trees. Rather than return silently wrong data,
Hibernate crashes.

**The fix: `List` → `Set`**

Changing the `@OneToMany` collections from `List` to `Set` (backed by `LinkedHashSet`)
gives Hibernate what it needs to deduplicate the JOIN rows:

```java
// ❌ Bag — crashes when fetching two at once with @EntityGraph
private List<Post> posts = new ArrayList<>();

// ✅ Set — safe to join alongside other collections
private Set<Post> posts = new LinkedHashSet<>();
```

With `Set`, Hibernate can collapse the Cartesian product rows back into distinct
entities using set equality, and the same `@EntityGraph` that crashed before works
without issue.

This app deliberately keeps the main `User`/`Post`/`Image` entities using `List` so
the crash is reproducible at `GET /api/bag-problem/crash`. The `SetUser`/`SetPost`/
`SetImage` entities map the same tables with `Set`, and
`GET /api/bag-problem/fix-with-set` shows the identical `@EntityGraph({"posts","posts.images"})`
succeeding — loading the full graph in a single query.

---

## JPA Relationships: Architectural Considerations

The N+1 and EAGER problems share a common root cause: **implicit query behaviour**.
When annotations on an entity drive queries automatically, those queries are easy to
miss in code review and difficult to audit at scale. The following three approaches
represent progressively more explicit strategies for dealing with this.

**Level 1 — LAZY everywhere, fetch explicitly per query**

This is the standard JPA approach and what this app demonstrates. Keep all associations,
set everything to `LAZY`, and use `JOIN FETCH` or `@EntityGraph` on the specific
repository methods that need the related data. You still have the full JPA feature set;
the discipline is in being explicit about every fetch.

Works well when the team understands the problem, code reviews check fetch strategies,
and SQL logging is on in development.

**Level 2 — Drop `@OneToMany`, keep `@ManyToOne`**

`@OneToMany` is where N+1 explosions originate — a collection loaded per row.
`@ManyToOne` is much lower risk (one extra row, not N rows) and is manageable when kept
`LAZY`. A common defensive architecture removes all collection-side relationships and
replaces them with explicit repository methods:

```java
// ❌ Remove — implicit collection loading is the primary N+1 source
@OneToMany(mappedBy = "user")
private List<Post> posts;

// ✅ Keep — one join per query, manageable when LAZY
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
private User user;
```

```java
// Explicit query replaces user.getPosts()
@Query("SELECT p FROM Post p WHERE p.userId = :userId")
List<Post> findByUserId(Long userId);
```

Without `user.getPosts()`, it becomes impossible to accidentally trigger an N+1 on the
collection. You have to write the query, so the query is always visible.

**Level 3 — Remove all relationships, use only FK columns**

Replace every `@ManyToOne` with a plain `Long` foreign key column. No relationships,
no lazy proxies, no implicit loading of any kind. Every join is an explicit query.

```java
// Instead of:  @ManyToOne private User user;
@Column(name = "user_id", nullable = false)
private Long userId;
```

This is the design that **Spring Data JDBC** is built around — no lazy loading, no
cascades, no ORM magic. It works particularly well with DDD aggregate boundaries, where
cross-aggregate navigation should always be explicit. The trade-off is that cascade
saves require saving each entity manually. Delete cascades are handled more reliably
by `ON DELETE CASCADE` in the schema anyway, since they apply regardless of which tool
or service touches the database.

**Which level to choose**

| Approach | Risk of N+1 | Boilerplate | Cascade support |
|---|---|---|---|
| LAZY + explicit fetching | Low (with discipline) | Low | ✅ Full |
| Drop `@OneToMany` | Very low | Medium | Partial |
| Remove all relationships | None | High | Deletes via `ON DELETE CASCADE`; saves explicit |

There is no universally correct answer — it depends on team discipline, codebase
complexity, and how much you trust implicit framework behaviour. What is always true:
defaulting to `LAZY` and fetching explicitly is safer than reaching for `EAGER`.

---

## The N+1 Problem is Not JPA-Specific

Every ORM in every language has this problem. The pattern is always the same: a
relationship that is not loaded upfront gets accessed inside a loop, and the ORM fires
one query per iteration.

**One important distinction from JPA worth noting.** In JPA, `FetchType.EAGER` is a
static, model-level declaration — it is baked into the entity annotation and applies
to every query on that entity with no per-query opt-out. In every other ORM below,
there is no equivalent. What those ORMs call "eager loading" is always a dynamic,
per-query decision made at the call site:

```typescript
User.query().preload('posts')  // this query loads posts
User.query()                   // this query does not
```

This per-query approach is actually what `JOIN FETCH` and `@EntityGraph` bring to JPA
— which is why they are the correct fix. The static `FetchType.EAGER` shortcut in JPA
is an anomaly, and an easy trap to fall into.

### JavaScript / TypeScript — AdonisJS Lucid ORM · [lucid.adonisjs.com/docs/relationships#preload-relationship](https://lucid.adonisjs.com/docs/relationships#preload-relationship)

Relationships are declared on the model with decorators:

```typescript
// app/models/user.ts
export default class User extends BaseModel {
  @hasMany(() => Post)
  declare posts: HasMany<typeof Post>

  @hasMany(() => Image, {
    foreignKey: 'imageableId',
    onQuery: (query) => query.where('imageable_type', 'users'),
  })
  declare images: HasMany<typeof Image>
}

// app/models/post.ts
export default class Post extends BaseModel {
  @belongsTo(() => User)
  declare user: BelongsTo<typeof User>

  @hasMany(() => Image, {
    foreignKey: 'imageableId',
    onQuery: (query) => query.where('imageable_type', 'posts'),
  })
  declare images: HasMany<typeof Image>
}
```

Without `preload()`, calling `user.load('posts')` inside a loop fires one query per
user:

```typescript
// ❌ N+1 — 1 + N + N queries
const users = await User.all()
for (const user of users) {
  await user.load('posts')           // 1 query per user
  for (const post of user.posts) {
    await post.load('images')        // 1 query per post
  }
}
```

> In practice, `load()` calls rarely appear this close together — they are typically
> scattered across service methods, making the query count even harder to spot.

`preload()` resolves all relationships upfront, in one query per relationship:

```typescript
// ✅ Fix — 3 queries total
const users = await User.query()
  .preload('posts', (q) => q.preload('images'))
  .preload('images')
// Total: 3 queries — one per relationship
```

### Ruby on Rails — ActiveRecord · [guides.rubyonrails.org/active_record_querying.html#eager-load](https://guides.rubyonrails.org/active_record_querying.html#eager-load)

```ruby
# ❌ N+1 — triggers one query per user
users = User.all
users.each { |u| puts u.posts.count }

# ✅ Fix — eager load with includes
users = User.includes(:images, posts: :images).all
```

### Python — Django ORM · [docs.djangoproject.com/.../querysets/#prefetch-related](https://docs.djangoproject.com/en/6.0/ref/models/querysets/#prefetch-related)

```python
# ❌ N+1
for user in User.objects.all():
    print(user.post_set.count())  # one query per user

# ✅ Fix — prefetch_related
users = User.objects.prefetch_related('posts__images', 'images').all()
```

### PHP — Laravel Eloquent · [laravel.com/docs/12.x/eloquent-relationships#eager-loading](https://laravel.com/docs/12.x/eloquent-relationships#eager-loading)

```php
// ❌ N+1 — one query per user, then one per post
$users = User::all();
foreach ($users as $user) {
    foreach ($user->posts as $post) { echo $post->images->count(); }
}

// ✅ Fix — with() eager loads in a few queries
$users = User::with(['posts.images', 'images'])->get();
```

### Swift — Vapor Fluent ORM · [docs.vapor.codes/fluent/relations/#eager-loading](https://docs.vapor.codes/fluent/relations/#eager-loading)

```swift
// ❌ N+1 — get(on:) in a loop fires one query per row
let users = try await User.query(on: db).all()
for user in users {
    let posts = try await user.$posts.get(on: db)
}

// ✅ Fix — with() eager loads nested relations
let users = try await User.query(on: db)
    .with(\.$posts) { post in post.with(\.$images) }
    .with(\.$images)
    .all()
```

### Go — GORM · [gorm.io/docs/preload.html](https://gorm.io/docs/preload.html)

```go
// ❌ N+1 — Association().Find() in a loop fires one query per row
var users []User
db.Find(&users)
for i := range users {
    db.Model(&users[i]).Association("Posts").Find(&users[i].Posts)
}

// ✅ Fix — Preload with dot notation for nested relations
db.Preload("Posts.Images").Preload("Images").Find(&users)
```

---

## N+1 Without an ORM

The N+1 pattern is not something an ORM introduces — it is a structural problem with
how queries are written. You can reproduce it with nothing but raw SQL and a loop.

```java
// 1 query — fetch 25 users
List<User> users = jdbcTemplate.query(
    "SELECT * FROM users LIMIT 25", userMapper
);

// 25 queries — one per user
for (User user : users) {
    List<Post> posts = jdbcTemplate.query(
        "SELECT * FROM posts WHERE user_id = ? ORDER BY created_at DESC LIMIT 2",
        postMapper, user.getId()
    );
    user.setPosts(posts);
}
// Total: 26 queries
```

The loop is obvious here, but in a real codebase the outer query and the inner query
often live in different methods or different layers of the application. The pattern only
becomes visible when you look at what is actually sent to the database.

> In practice, the inner query rarely lives this close to the outer one — it is
> typically buried in a separate service or repository method, making the query count
> even harder to spot.

The fix is the same principle as `JOIN FETCH` and `preload()`: fetch all the related
rows in one batched query, then associate them in memory.

```java
// 1 query — fetch 25 users
List<User> users = jdbcTemplate.query(
    "SELECT * FROM users LIMIT 25", userMapper
);

// 1 query — fetch all their posts at once
List<Long> userIds = users.stream().map(User::getId).toList();
List<Post> posts = namedJdbc.query(
    "SELECT * FROM posts WHERE user_id IN (:ids) ORDER BY created_at DESC",
    Map.of("ids", userIds), postMapper
);

// In-memory grouping — no extra queries
Map<Long, List<Post>> postsByUser = posts.stream()
    .collect(Collectors.groupingBy(Post::getUserId));

for (User user : users) {
    List<Post> latest = postsByUser
        .getOrDefault(user.getId(), List.of())
        .stream().limit(2).toList();
    user.setPosts(latest);
}
// Total: 2 queries
```

Whether you use an ORM or raw SQL, the solution is the same: **avoid querying inside
a loop**. Fetch the full set of related rows once, then do the mapping in memory.
