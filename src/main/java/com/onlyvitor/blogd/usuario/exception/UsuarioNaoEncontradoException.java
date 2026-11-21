package com.onlyvitor.blogd.usuario.exception;

public class UsuarioNaoEncontradoException extends RuntimeException {

    public UsuarioNaoEncontradoException(Long id) {
        super("Usuário não encontrado com id: " + id);
    }

    public UsuarioNaoEncontradoException(String usuario) {
        super("Usuário não encontrado com nome de usuário: " + usuario);
    }
}
