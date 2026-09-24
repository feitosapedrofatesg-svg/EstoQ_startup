package com.estoq.business.plataforma;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nova senha aplicada ao primeiro ADMIN ativo do restaurante. */
public record RedefinirSenhaAdminDTO(@NotBlank @Size(min = 8, max = 72) String senha) {
}