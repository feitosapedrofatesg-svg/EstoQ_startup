package com.estoq.business.plataforma;

import jakarta.validation.constraints.NotNull;

/** Ativa ou suspende um restaurante pelo painel PLATAFORMA. */
public record AtualizarRestauranteStatusDTO(@NotNull boolean ativo) {
}