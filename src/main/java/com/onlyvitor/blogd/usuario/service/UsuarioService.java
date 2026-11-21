package com.onlyvitor.blogd.usuario.service;

import com.onlyvitor.blogd.usuario.domain.Usuario;
import com.onlyvitor.blogd.usuario.dto.UsuarioRequest;
import com.onlyvitor.blogd.usuario.dto.UsuarioResponse;
import com.onlyvitor.blogd.usuario.exception.UsuarioJaExistenteException;
import com.onlyvitor.blogd.usuario.exception.UsuarioNaoEncontradoException;
import com.onlyvitor.blogd.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public List<UsuarioResponse> listarTodos() {
        return usuarioRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UsuarioResponse buscarPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));
        return toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        if (usuarioRepository.existsByUsuario(request.getUsuario())) {
            throw new UsuarioJaExistenteException(request.getUsuario());
        }

        Usuario usuario = Usuario.builder()
                .usuario(request.getUsuario())
                .senha(request.getSenha())
                .email(request.getEmail())
                .role(request.getRole())
                .build();

        Usuario salvo = usuarioRepository.save(usuario);
        return toResponse(salvo);
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        if (!usuario.getUsuario().equals(request.getUsuario())
                && usuarioRepository.existsByUsuario(request.getUsuario())) {
            throw new UsuarioJaExistenteException(request.getUsuario());
        }

        usuario.setUsuario(request.getUsuario());
        usuario.setSenha(request.getSenha());
        usuario.setEmail(request.getEmail());
        usuario.setRole(request.getRole());

        Usuario atualizado = usuarioRepository.save(usuario);
        return toResponse(atualizado);
    }

    @Transactional
    public void deletar(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new UsuarioNaoEncontradoException(id);
        }
        usuarioRepository.deleteById(id);
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return UsuarioResponse.builder()
                .id(usuario.getId())
                .usuario(usuario.getUsuario())
                .email(usuario.getEmail())
                .role(usuario.getRole())
                .build();
    }
}
