package com.estoq.business.usuarios;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioUpdateDTO(
        @NotNull Long version,
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(min = 8, max = 72) String senha,
        @NotNull Perfil perfil,
        @NotNull Boolean ativo) {
}