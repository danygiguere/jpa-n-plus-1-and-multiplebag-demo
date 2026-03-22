CREATE TABLE IF NOT EXISTS users (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email     VARCHAR(255) NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS posts (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    title   VARCHAR(255) NOT NULL,
    content TEXT,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS images (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    url     VARCHAR(1000) NOT NULL,
    post_id BIGINT,
    user_id BIGINT,
    CONSTRAINT fk_images_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT fk_images_user FOREIGN KEY (user_id) REFERENCES users (id)
);
