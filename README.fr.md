# Spring Boot — Démonstration JPA N+1 & MultipleBag

🇬🇧 [English](README.md) | 🇫🇷 Français

Ce projet illustre deux des problèmes de requêtes les plus courants et les plus mal
compris dans Hibernate/JPA : le **problème N+1** et le **problème MultipleBag**. Les
deux sont silencieux par défaut, les deux s'introduisent facilement sous la pression
des délais, et les deux ont des solutions bien connues.

L'application est une API REST Spring Boot 3 avec la journalisation SQL activée. Chaque
endpoint est conçu pour montrer un comportement précis dans la console — la version
problématique d'abord, la correction juste à côté. Importez la collection Postman,
démarrez l'application et observez le nombre de requêtes.

---

## Table des matières

- [Le problème N+1](#le-problème-n1)
- [Pourquoi c'est important](#pourquoi-cest-important)
- [Démarrage](#démarrage)
- [Endpoints](#endpoints)
  - [Utilisateurs](#utilisateurs)
  - [Articles](#articles)
  - [Problème Bag](#problème-bag)
- [Valeurs par défaut JPA des types de chargement](#valeurs-par-défaut-jpa-des-types-de-chargement)
- [Les deux solutions](#les-deux-solutions)
  - [Solution 1 — JPQL JOIN FETCH](#solution-1--jpql-join-fetch)
  - [Solution 2 — @EntityGraph](#solution-2--entitygraph)
- [Le piège EAGER](#le-piège-eager)
- [Le problème MultipleBag](#le-problème-multiplebag)
- [Considérations architecturales](#considérations-architecturales)
- [Le problème N+1 n'est pas propre à JPA](#le-problème-n1-nest-pas-propre-à-jpa)
- [N+1 sans ORM](#n1-sans-orm)

---

## Le problème N+1

Lorsque Hibernate charge une liste d'entités, les associations marquées `LAZY` ne sont
pas chargées immédiatement — elles sont représentées par un proxy et ne sont chargées
qu'au premier accès. C'est un comportement par défaut raisonnable. Le problème survient
lorsque vous accédez à une association lazy dans une boucle : Hibernate exécute un
`SELECT` par ligne plutôt que de tout charger d'emblée.

```java
List<User> users = userRepository.findAll(); // 1 requête

for (User user : users) {
    user.getPosts(); // 1 requête par utilisateur — silencieux, aucun avertissement
}
```

Avec 3 utilisateurs, cela donne 4 requêtes. Avec 100, cela en donne 101. La formule —
**1 + N** — est à l'origine du nom.

Ce qui rend ce problème particulièrement insidieux, c'est que le code paraît tout à
fait normal. Aucune exception, aucun message de log, aucun signe que quelque chose ne
va pas. Les requêtes supplémentaires ne deviennent visibles que lorsque la journalisation
SQL est activée — ce que la plupart des développeurs n'ont pas en production et oublient
souvent de vérifier en développement.

---

## Pourquoi c'est important

Le problème N+1 est trompeusement peu coûteux dans un environnement de développement
avec peu de données. Il devient rapidement onéreux en production, là où N est grand et
où les associations sont souvent imbriquées.

| Scénario | Entités | Associations accédées | Total requêtes |
|---|---|---|---|
| Cette démo — utilisateurs | 3 utilisateurs | posts + images | **7** (1 + 3 + 3) |
| Cette démo — articles | 12 articles | images + auteur | **25** (1 + 12 + 12) |
| Liste de commandes e-commerce | 100 commandes | articles + client + livraison | **301** |
| Liste d'articles de blog (50/page) | 50 articles | auteur + tags + commentaires | **151** |
| Export utilisateurs admin (500) | 500 utilisateurs | rôles + dernière connexion + profil | **1 501** |
| Fil social (20 posts/page) | 20 posts | auteur + likes + commentaires + médias | **81** |

Une seule requête chargeant 100 commandes et accédant à trois associations génère 301
requêtes. L'endpoint fonctionne bien en phase de test, passe en production, et devient
un goulot d'étranglement pour la base de données quelques semaines plus tard, quand
le trafic augmente. À ce stade, les accès aux associations sont répartis entre plusieurs
méthodes de service et ne sont pas faciles à retrouver.

---

## Démarrage

**Prérequis :** Java 21+, Gradle (wrapper inclus)

```bash
./gradlew bootRun
```

L'application démarre sur `http://localhost:8080`. Chaque requête SQL exécutée par
Hibernate est affichée dans la console — c'est voulu. Observez le nombre de requêtes
évoluer entre les endpoints N+1 et les endpoints corrigés.

**Modèle de données** — trois entités, initialisées automatiquement au démarrage :

```
User
 ├── posts  (List<Post>)  — one-to-many, LAZY
 │    └── images  (List<Image>)  — one-to-many, LAZY
 └── images  (List<Image>)  — one-to-many, LAZY  (photos de profil)
```

| Entité | Nombre |
|---|---|
| Utilisateurs | 3 |
| Articles par utilisateur | 4 → **12 au total** |
| Images par article | 3 → **36 au total** |
| Images de profil par utilisateur | 2 → **6 au total** |

**Console H2** — base de données en mémoire, réinitialisée à chaque démarrage.

```
URL :       http://localhost:8080/h2-console
JDBC URL :  jdbc:h2:mem:n1db
Utilisateur : sa
Mot de passe : (vide)
```

**Configuration clé** (`application.properties`) :

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
server.error.include-message=always
```

---

## Endpoints

Importez `jpa-n1-demo.postman_collection.json` dans Postman (`{{baseUrl}}` = `http://localhost:8080`).

### Utilisateurs

| Endpoint | Ce qu'on observe |
|---|---|
| `GET /api/users/n-plus-one` | La console affiche **7 requêtes** : 1 pour les utilisateurs + 3 pour les posts + 3 pour les images |
| `GET /api/users/fetch-join` | La console affiche **1 requête** avec un `JOIN` — posts chargés avec les utilisateurs |
| `GET /api/users/entity-graph` | La console affiche **1 requête** via `@EntityGraph` — même résultat, sans JPQL |
| `GET /api/users/fetch-join-images` | La console affiche **1 requête** — images de profil chargées avec les utilisateurs |

### Articles

| Endpoint | Ce qu'on observe |
|---|---|
| `GET /api/posts/n-plus-one` | La console affiche **25 requêtes** : 1 pour les articles + 12 pour les images + 12 pour les auteurs |
| `GET /api/posts/fetch-join` | La console affiche **1 requête** — images chargées avec les articles |
| `GET /api/posts/entity-graph` | La console affiche **1 requête** — auteur et images chargés simultanément |

### Problème Bag

| Endpoint | Ce qu'on observe |
|---|---|
| `GET /api/bag-problem/crash` | Retourne **HTTP 422** avec le vrai message de `MultipleBagFetchException` |
| `GET /api/bag-problem/fix` | Retourne **HTTP 200** — charger un bag à la fois est toujours sûr |
| `GET /api/bag-problem/fix-with-set` | Retourne **HTTP 200** — entités basées sur `Set`, les deux collections en une seule requête |

---

## Valeurs par défaut JPA des types de chargement

Chaque association JPA possède un type de chargement qui contrôle le moment où
Hibernate charge les données associées. Lorsqu'on écrit une annotation sans argument
`fetch =`, JPA applique silencieusement ses propres valeurs par défaut :

| Association | Défaut | Risque |
|---|---|---|
| `@OneToMany()` | **LAZY** | Sûr — les données ne sont chargées que si vous accédez à la collection |
| `@ManyToMany()` | **LAZY** | Sûr |
| `@ManyToOne()` | **EAGER** ⚠️ | Toute requête sur `Post` charge aussi son `User`, systématiquement |
| `@OneToOne()` | **EAGER** ⚠️ | Toute requête du côté propriétaire charge aussi l'autre côté |

**LAZY** signifie qu'Hibernate ne charge pas l'association tant que vous n'y accédez
pas. La donnée associée reste non chargée — représentée par un proxy — jusqu'à l'appel
du getter, moment auquel Hibernate exécute un `SELECT` sur le champ. C'est efficace
quand vous n'avez pas besoin de la donnée. C'est la cause racine du N+1 lorsque ce
getter est appelé dans une boucle.

**EAGER** signifie qu'Hibernate charge l'association automatiquement à chaque requête,
que vous utilisiez le résultat ou non. Une simple requête de comptage, un endpoint
léger, une projection qui n'a besoin que de deux champs — tous paient silencieusement
le coût complet du JOIN.

Les deux valeurs par défaut dangereuses — `@ManyToOne` et `@OneToOne` — signifient que
sans configuration explicite, interroger `Post` chargera toujours son `User`. C'est
rarement ce que vous voulez dans tous les contextes. C'est pourquoi toutes les
associations de ce projet sont explicitement déclarées `LAZY`, en surchargeant les
valeurs par défaut de JPA :

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
private User user;
```

Comprendre ces valeurs par défaut facilite le raisonnement sur les
[solutions ci-dessous](#les-deux-solutions) et le [piège EAGER](#le-piège-eager).

---

## Les deux solutions

Le problème N+1 a deux solutions standard en JPA. Les deux consistent à dire à
Hibernate de charger l'association avec un `JOIN` au moment de la requête, plutôt que
paresseusement ligne par ligne.

### Solution 1 — JPQL `JOIN FETCH`

```java
@Query("SELECT DISTINCT u FROM User u JOIN FETCH u.posts")
List<User> findAllWithPostsFetchJoin();
```

Hibernate réécrit cela en une seule requête SQL avec `JOIN`. Le `DISTINCT` évite les
doublons d'objets `User` quand un utilisateur possède plusieurs posts. Contrôle total,
mais vous écrivez le JPQL vous-même.

### Solution 2 — `@EntityGraph`

```java
@EntityGraph(attributePaths = {"posts"})
@Query("SELECT DISTINCT u FROM User u")
List<User> findAllWithPostsEntityGraph();
```

Déclare le chargement anticipé comme métadonnée sur la méthode du repository plutôt
que dans la chaîne de requête. Hibernate génère le même `JOIN` en coulisses. Utile
quand vous souhaitez garder le JPQL propre ou réutiliser une requête de base avec
plusieurs stratégies de chargement.

Les deux approches produisent une seule requête au lieu de N+1. Le choix entre elles
relève principalement du style et de la complexité de vos requêtes.

---

## Le piège EAGER

Face au N+1, le réflexe courant est de passer l'association à `FetchType.EAGER`. Cela
ressemble à une correction. Ce n'en est pas une.

```java
// ❌ Ne corrige pas le N+1 — le masque et aggrave les choses
@OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
private List<Post> posts;
```

Avec `EAGER`, Hibernate charge l'association automatiquement sur chaque requête — y
compris celles où vous n'utilisez jamais l'association. Un simple appel à
`userRepository.findAll()`, une requête de comptage, un endpoint de recherche qui n'a
besoin que de l'adresse e-mail de l'utilisateur — tous chargent silencieusement tous
les posts pour chaque utilisateur.

```
SELECT * FROM users
SELECT * FROM posts WHERE user_id = 1
SELECT * FROM posts WHERE user_id = 2
SELECT * FROM posts WHERE user_id = 3
```

Le N+1 est maintenant ancré dans l'entité elle-même et ne peut plus être désactivé
sans écrire du JPQL personnalisé. Chaque requête paye le coût complet, qu'elle ait
besoin des données ou non.

Ce problème s'accumule avec celui des valeurs par défaut. Comme indiqué dans la section
[Valeurs par défaut JPA](#valeurs-par-défaut-jpa-des-types-de-chargement), `@ManyToOne`
est déjà `EAGER` sans aucune annotation — ce qui signifie que chaque requête de posts
charge silencieusement son auteur, avant même que vous touchiez à `FetchType.EAGER`.
Définir `EAGER` explicitement sur un `@OneToMany` s'ajoute par-dessus.

`EAGER` n'est approprié que lorsque les trois conditions suivantes sont réunies : 100 %
des requêtes sur cette entité ont réellement besoin des données associées, la collection
est petite et bornée, et vous êtes pleinement conscient du `JOIN` ajouté à chaque
requête. Dans la plupart des cas, aucune de ces conditions n'est remplie.

> **Règle à retenir :** commencez toujours avec `LAZY`. Chargez les associations
> explicitement avec `JOIN FETCH` ou `@EntityGraph`, uniquement sur les requêtes qui
> en ont réellement besoin.

---

## Le problème MultipleBag

Une fois le N+1 compris et `@EntityGraph` utilisé pour le corriger, vous pouvez
rencontrer un second problème : `MultipleBagFetchException`.

Hibernate utilise le terme **bag** pour désigner un `java.util.List` sur une
association `@OneToMany` — une collection non ordonnée qui tolère les doublons dans les
résultats. Lorsque vous tentez de charger deux collections de type bag simultanément
avec un seul `@EntityGraph` ou `JOIN FETCH`, Hibernate lève une exception et refuse
d'exécuter la requête :

```
org.hibernate.loader.MultipleBagFetchException:
  cannot simultaneously fetch multiple bags: [User.posts, Post.images]
```

La raison est mathématique. Un `JOIN` sur une collection multiplie les lignes du
résultat — une ligne par combinaison parent/enfant. Un second `JOIN` multiplie à
nouveau. Deux bags produisent un produit cartésien non borné qu'Hibernate ne peut pas
regrouper en toute sécurité en arbres d'entités distincts. Plutôt que de retourner
silencieusement des données incorrectes, Hibernate plante.

**La correction : `List` → `Set`**

Passer les collections `@OneToMany` de `List` à `Set` (implémenté par `LinkedHashSet`)
donne à Hibernate ce dont il a besoin pour dédupliquer les lignes du JOIN :

```java
// ❌ Bag — plante lors du chargement simultané de deux collections avec @EntityGraph
private List<Post> posts = new ArrayList<>();

// ✅ Set — sûr à joindre avec d'autres collections
private Set<Post> posts = new LinkedHashSet<>();
```

Avec `Set`, Hibernate peut regrouper les lignes du produit cartésien en entités
distinctes grâce à l'égalité ensembliste, et le même `@EntityGraph` qui plantait
auparavant fonctionne sans problème.

Cette application conserve intentionnellement les entités principales `User`/`Post`/
`Image` avec `List` pour que le crash soit reproductible via `GET /api/bag-problem/crash`.
Les entités `SetUser`/`SetPost`/`SetImage` mappent les mêmes tables avec `Set`, et
`GET /api/bag-problem/fix-with-set` montre le même `@EntityGraph({"posts","posts.images"})`
réussir — chargeant le graphe complet en une seule requête.

---

## Considérations architecturales

Les problèmes N+1 et EAGER partagent une cause racine commune : **le comportement de
requête implicite**. Lorsque des annotations sur une entité pilotent automatiquement
des requêtes, ces requêtes sont faciles à manquer en revue de code et difficiles à
auditer à grande échelle. Les trois approches suivantes représentent des stratégies
progressivement plus explicites pour y faire face.

**Niveau 1 — LAZY partout, chargement explicite par requête**

C'est l'approche JPA standard et ce que cette application démontre. Conservez toutes
les associations, passez tout en `LAZY`, et utilisez `JOIN FETCH` ou `@EntityGraph`
sur les méthodes de repository spécifiques qui ont besoin des données associées. Vous
conservez tout le jeu de fonctionnalités JPA ; la discipline consiste à être explicite
sur chaque chargement.

Fonctionne bien quand l'équipe comprend le problème, que les revues de code vérifient
les stratégies de chargement, et que la journalisation SQL est activée en développement.

**Niveau 2 — Supprimer `@OneToMany`, conserver `@ManyToOne`**

`@OneToMany` est là où les explosions N+1 se produisent — une collection chargée par
ligne. `@ManyToOne` est bien moins risqué (une ligne supplémentaire, pas N) et est
gérable en `LAZY`. Une architecture défensive courante supprime toutes les relations
côté collection et les remplace par des méthodes de repository explicites :

```java
// ❌ Supprimer — le chargement implicite de collections est la source principale de N+1
@OneToMany(mappedBy = "user")
private List<Post> posts;

// ✅ Conserver — un join par requête, gérable en LAZY
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id")
private User user;
```

```java
// Une requête explicite remplace user.getPosts()
@Query("SELECT p FROM Post p WHERE p.userId = :userId")
List<Post> findByUserId(Long userId);
```

Sans `user.getPosts()`, il devient impossible de déclencher accidentellement un N+1
sur la collection. Vous devez écrire la requête, donc la requête est toujours visible.

**Niveau 3 — Supprimer toutes les relations, utiliser uniquement des colonnes FK**

Remplacez chaque `@ManyToOne` par une simple colonne de clé étrangère `Long`. Aucune
relation, aucun proxy lazy, aucun chargement implicite d'aucune sorte. Chaque jointure
est une requête explicite.

```java
// Au lieu de :  @ManyToOne private User user;
@Column(name = "user_id", nullable = false)
private Long userId;
```

C'est le modèle autour duquel **Spring Data JDBC** est conçu — pas de lazy loading, pas
de cascades, pas de magie ORM. Il fonctionne particulièrement bien avec les frontières
d'agrégats DDD, où la navigation inter-agrégats doit toujours être explicite. La
contrepartie est que les sauvegardes en cascade nécessitent de persister chaque entité
manuellement. Les suppressions en cascade sont gérées plus fiablement par
`ON DELETE CASCADE` dans le schéma, car elles s'appliquent quel que soit l'outil ou
le service qui touche la base de données.

**Quel niveau choisir**

| Approche | Risque de N+1 | Code supplémentaire | Support des cascades |
|---|---|---|---|
| LAZY + chargement explicite | Faible (avec discipline) | Faible | ✅ Complet |
| Supprimer `@OneToMany` | Très faible | Moyen | Partiel |
| Supprimer toutes les relations | Aucun | Élevé | Suppressions via `ON DELETE CASCADE` ; sauvegardes explicites |

Il n'y a pas de réponse universellement correcte — cela dépend de la discipline de
l'équipe, de la complexité du code, et de la confiance accordée au comportement
implicite du framework. Ce qui est toujours vrai : partir de `LAZY` et charger
explicitement est plus sûr que de recourir à `EAGER`.

---

## Le problème N+1 n'est pas propre à JPA

Tous les ORM dans tous les langages ont ce problème. Le schéma est toujours le même :
une relation non chargée en amont est accédée dans une boucle, et l'ORM exécute une
requête par itération.

**Une distinction importante par rapport à JPA.** Dans JPA, `FetchType.EAGER` est une
déclaration statique au niveau du modèle — ancrée dans l'annotation de l'entité et
appliquée à chaque requête sur cette entité sans possibilité de l'annuler par requête.
Dans tous les autres ORM ci-dessous, il n'existe pas d'équivalent. Ce que ces ORM
appellent "eager loading" est toujours une décision dynamique, prise à l'appel :

```typescript
User.query().preload('posts')  // cette requête charge les posts
User.query()                   // cette requête ne les charge pas
```

Cette approche par requête est exactement ce que `JOIN FETCH` et `@EntityGraph`
apportent à JPA — c'est pourquoi ce sont les bonnes solutions. Le raccourci statique
`FetchType.EAGER` dans JPA est une anomalie, et un piège dans lequel il est facile
de tomber.

### JavaScript / TypeScript — AdonisJS Lucid ORM · [lucid.adonisjs.com/docs/relationships#preload-relationship](https://lucid.adonisjs.com/docs/relationships#preload-relationship)

Les relations sont déclarées sur le modèle avec des décorateurs :

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

Sans `preload()`, appeler `user.load('posts')` dans une boucle exécute une requête par
utilisateur :

```typescript
// ❌ N+1 — 1 + N + N requêtes
const users = await User.all()
for (const user of users) {
  await user.load('posts')           // 1 requête par utilisateur
  for (const post of user.posts) {
    await post.load('images')        // 1 requête par article
  }
}
```

> En pratique, les appels à `load()` sont rarement aussi proches l'un de l'autre — ils
> sont généralement dispersés entre différentes méthodes de service, ce qui rend le
> nombre de requêtes encore plus difficile à repérer.

`preload()` résout toutes les relations en amont, en une requête par relation :

```typescript
// ✅ Correction — 3 requêtes au total
const users = await User.query()
  .preload('posts', (q) => q.preload('images'))
  .preload('images')
// Total : 3 requêtes — une par relation
```

### Ruby on Rails — ActiveRecord · [guides.rubyonrails.org/active_record_querying.html#eager-load](https://guides.rubyonrails.org/active_record_querying.html#eager-load)

```ruby
# ❌ N+1 — déclenche une requête par utilisateur
users = User.all
users.each { |u| puts u.posts.count }

# ✅ Correction — chargement anticipé avec includes
users = User.includes(:images, posts: :images).all
```

### Python — Django ORM · [docs.djangoproject.com/.../querysets/#prefetch-related](https://docs.djangoproject.com/en/6.0/ref/models/querysets/#prefetch-related)

```python
# ❌ N+1
for user in User.objects.all():
    print(user.post_set.count())  # une requête par utilisateur

# ✅ Correction — prefetch_related
users = User.objects.prefetch_related('posts__images', 'images').all()
```

### PHP — Laravel Eloquent · [laravel.com/docs/12.x/eloquent-relationships#eager-loading](https://laravel.com/docs/12.x/eloquent-relationships#eager-loading)

```php
// ❌ N+1 — une requête par utilisateur, puis une par article
$users = User::all();
foreach ($users as $user) {
    foreach ($user->posts as $post) { echo $post->images->count(); }
}

// ✅ Correction — with() charge en anticipé en quelques requêtes
$users = User::with(['posts.images', 'images'])->get();
```

### Swift — Vapor Fluent ORM · [docs.vapor.codes/fluent/relations/#eager-loading](https://docs.vapor.codes/fluent/relations/#eager-loading)

```swift
// ❌ N+1 — get(on:) dans une boucle exécute une requête par ligne
let users = try await User.query(on: db).all()
for user in users {
    let posts = try await user.$posts.get(on: db)
}

// ✅ Correction — with() charge les relations imbriquées en anticipé
let users = try await User.query(on: db)
    .with(\.$posts) { post in post.with(\.$images) }
    .with(\.$images)
    .all()
```

### Go — GORM · [gorm.io/docs/preload.html](https://gorm.io/docs/preload.html)

```go
// ❌ N+1 — Association().Find() dans une boucle exécute une requête par ligne
var users []User
db.Find(&users)
for i := range users {
    db.Model(&users[i]).Association("Posts").Find(&users[i].Posts)
}

// ✅ Correction — Preload avec notation pointée pour les relations imbriquées
db.Preload("Posts.Images").Preload("Images").Find(&users)
```

---

## N+1 sans ORM

Le problème N+1 n'est pas quelque chose qu'un ORM introduit — c'est un problème
structurel dans la façon dont les requêtes sont écrites. Vous pouvez le reproduire avec
rien d'autre que du SQL brut et une boucle.

```java
// 1 requête — récupérer 25 utilisateurs
List<User> users = jdbcTemplate.query(
    "SELECT * FROM users LIMIT 25", userMapper
);

// 25 requêtes — une par utilisateur
for (User user : users) {
    List<Post> posts = jdbcTemplate.query(
        "SELECT * FROM posts WHERE user_id = ? ORDER BY created_at DESC LIMIT 2",
        postMapper, user.getId()
    );
    user.setPosts(posts);
}
// Total : 26 requêtes
```

La boucle est évidente ici, mais dans une vraie base de code, la requête externe et la
requête interne se trouvent souvent dans des méthodes ou des couches différentes de
l'application. Le schéma ne devient visible que lorsqu'on regarde ce qui est réellement
envoyé à la base de données.

> En pratique, la requête interne se trouve rarement aussi près de la requête externe —
> elle est généralement enfouie dans un service ou une méthode de repository distinct,
> ce qui rend le nombre de requêtes encore plus difficile à repérer.

La correction repose sur le même principe que `JOIN FETCH` et `preload()` : récupérer
toutes les lignes associées en une seule requête groupée, puis faire l'association en
mémoire.

```java
// 1 requête — récupérer 25 utilisateurs
List<User> users = jdbcTemplate.query(
    "SELECT * FROM users LIMIT 25", userMapper
);

// 1 requête — récupérer tous leurs articles en une fois
List<Long> userIds = users.stream().map(User::getId).toList();
List<Post> posts = namedJdbc.query(
    "SELECT * FROM posts WHERE user_id IN (:ids) ORDER BY created_at DESC",
    Map.of("ids", userIds), postMapper
);

// Regroupement en mémoire — aucune requête supplémentaire
Map<Long, List<Post>> postsByUser = posts.stream()
    .collect(Collectors.groupingBy(Post::getUserId));

for (User user : users) {
    List<Post> latest = postsByUser
        .getOrDefault(user.getId(), List.of())
        .stream().limit(2).toList();
    user.setPosts(latest);
}
// Total : 2 requêtes
```

Que vous utilisiez un ORM ou du SQL brut, la solution est la même : **évitez de
requêter dans une boucle**. Récupérez l'ensemble des lignes associées en une seule
fois, puis faites le mapping en mémoire.
