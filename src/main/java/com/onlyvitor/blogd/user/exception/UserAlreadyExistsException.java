package com.onlyvitor.blogd.usuario.exception;

public class UsuarioJaExistenteException extends RuntimeException {

    public UsuarioJaExistenteException(String usuario) {
        super("Já existe um usuário com o nome de usuário: " + usuario);
    }
}
