package com.estoq.business.registro;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro aberto de um restaurante novo na plataforma. */
public record RegistroRequestDTO(
        @NotBlank @Size(max = 120) String nomeLoja,
        @NotBlank @Size(max = 120) String nomeResponsavel,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String senha) {
}