package com.onlyvitor.blogd.usuario.dto;

import com.onlyvitor.blogd.usuario.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRequest {

    @NotBlank(message = "O nome de usuário é obrigatório")
    @Size(min = 3, max = 50, message = "O nome de usuário deve ter entre 3 e 50 caracteres")
    private String usuario;

    @NotBlank(message = "A senha é obrigatória")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?])(?=.*[A-Z])(?=.*[a-z]).{8,}$",
        message = "A senha deve ter pelo menos 8 caracteres, incluindo uma letra maiúscula, uma letra minúscula, um número e um caractere especial"
    )
    private String senha;

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "O e-mail deve ser um endereço de e-mail válido")
    private String email;

    @NotNull(message = "O papel é obrigatório")
    private Role role;
}
