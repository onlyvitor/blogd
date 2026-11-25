ALTER TABLE posts
    ADD COLUMN author_id BIGINT NULL,
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN access_key VARCHAR(64) NULL;

-- posts without any author cannot be managed under the new rules
DELETE FROM posts WHERE id NOT IN (SELECT post_id FROM post_authors);

-- the main author is the earliest author, matching the previous id ordering
UPDATE posts p
SET p.author_id = (SELECT MIN(pa.user_id) FROM post_authors pa WHERE pa.post_id = p.id);

ALTER TABLE posts
    MODIFY COLUMN author_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users (id);

CREATE TABLE post_coauthors (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_post_coauthors_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_coauthors_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT INTO post_coauthors (post_id, user_id)
SELECT pa.post_id, pa.user_id
FROM post_authors pa
JOIN posts p ON p.id = pa.post_id
WHERE pa.user_id <> p.author_id;

CREATE TABLE post_deletion_acceptances (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_post_deletion_acceptances_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_deletion_acceptances_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

DROP TABLE post_authors;
