CREATE TABLE post_related_posts (
    post_id BIGINT NOT NULL,
    related_post_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, related_post_id),
    CONSTRAINT fk_post_related_posts_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_related_posts_related FOREIGN KEY (related_post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT ck_post_related_posts_not_self CHECK (post_id <> related_post_id)
) ENGINE=InnoDB;
