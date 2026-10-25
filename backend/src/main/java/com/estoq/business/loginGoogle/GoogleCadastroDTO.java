package com.estoq.business.loginGoogle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleCadastroDTO(
        @NotBlank @Size(max = 120) String nomeLoja,
        @NotBlank @Size(max = 120) String nomeResponsavel) {}
