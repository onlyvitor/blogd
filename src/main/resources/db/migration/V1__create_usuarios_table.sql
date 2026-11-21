CREATE TABLE usuarios (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario VARCHAR(50) NOT NULL,
    senha VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_usuario UNIQUE (usuario)
) ENGINE=InnoDB;
