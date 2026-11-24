CREATE TABLE post_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    name VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_post_attachments_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
) ENGINE=InnoDB;
