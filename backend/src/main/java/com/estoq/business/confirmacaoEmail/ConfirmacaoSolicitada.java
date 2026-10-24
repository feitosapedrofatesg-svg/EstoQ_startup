package com.estoq.business.confirmacaoEmail;

/** Só em memória, por poucos segundos. Não registrar este evento em logs. */
public record ConfirmacaoSolicitada(Long usuarioId, String email, String token) {}
