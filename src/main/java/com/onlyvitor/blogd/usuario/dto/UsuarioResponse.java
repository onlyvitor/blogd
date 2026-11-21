package com.onlyvitor.blogd.usuario.dto;

import com.onlyvitor.blogd.usuario.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {

    private Long id;
    private String usuario;
    private String email;
    private Role role;
}
