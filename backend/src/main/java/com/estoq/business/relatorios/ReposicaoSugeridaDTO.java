package com.estoq.business.relatorios;

import com.estoq.business.produtos.UnidadeMedida;

import java.math.BigDecimal;

public record ReposicaoSugeridaDTO(Long produtoId, String produtoNome, String categoriaNome, UnidadeMedida unidadeMedida,
        BigDecimal saldoAtual, BigDecimal estoqueMinimo, BigDecimal consumoMedioDiario, int diasReposicao,
        BigDecimal quantidadeSugerida) {
}