package com.nplus1.config;

import com.nplus1.entity.Image;
import com.nplus1.entity.Post;
import com.nplus1.entity.User;
import com.nplus1.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final UserRepository userRepository;

    /**
     * Seeds the database on startup:
     *  - 3 users
     *  - 4 posts per user
     *  - 3 images per post
     *  - 2 profile images per user (user-level images)
     *
     * Total queries WITHOUT N+1 fix:
     *   GET /users → 1 (users) + 3 (posts per user) + 3 (images per user) = 7 queries
     *   GET /posts → 1 (posts) + 12 (images per post) + 12 (user per post) = 25 queries
     */
    @Bean
    CommandLineRunner seed() {
        return args -> {
            if (userRepository.count() > 0) return;

            for (int i = 1; i <= 3; i++) {
                User user = User.builder()
                        .fullName("User " + i)
                        .email("user" + i + "@example.com")
                        .posts(new ArrayList<>())
                        .images(new ArrayList<>())
                        .build();

                // 4 posts per user, each with 3 images
                for (int j = 1; j <= 4; j++) {
                    Post post = Post.builder()
                            .title("Post " + j + " by User " + i)
                            .content("This is the content of post " + j + " written by user " + i + ".")
                            .user(user)
                            .images(new ArrayList<>())
                            .build();

                    for (int k = 1; k <= 3; k++) {
                        Image img = Image.builder()
                                .url("https://picsum.photos/seed/post" + j + "img" + k + "/400/300")
                                .post(post)
                                .build();
                        post.getImages().add(img);
                    }
                    user.getPosts().add(post);
                }

                // 2 profile images per user
                for (int k = 1; k <= 2; k++) {
                    Image profileImg = Image.builder()
                            .url("https://picsum.photos/seed/user" + i + "img" + k + "/200/200")
                            .user(user)
                            .build();
                    user.getImages().add(profileImg);
                }

                userRepository.save(user);
            }

            log.info("Seeded {} users with posts and images", userRepository.count());
        };
    }
}
