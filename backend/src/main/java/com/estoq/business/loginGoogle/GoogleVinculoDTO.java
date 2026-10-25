package com.estoq.business.loginGoogle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleVinculoDTO(@NotBlank @Size(max = 72) String senha) {}
