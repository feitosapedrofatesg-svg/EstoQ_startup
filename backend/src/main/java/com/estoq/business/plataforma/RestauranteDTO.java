package com.estoq.business.plataforma;

import java.time.LocalDateTime;

/** Restaurante visto pelo painel PLATAFORMA. */
public record RestauranteDTO(Long id, String nome, boolean ativo, LocalDateTime dataHoraCriacao, long totalUsuarios) {
}