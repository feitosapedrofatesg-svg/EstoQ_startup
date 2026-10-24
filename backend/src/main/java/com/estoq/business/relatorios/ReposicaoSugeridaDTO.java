package com.estoq.business.relatorios;

import com.estoq.business.produtos.UnidadeMedida;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReposicaoSugeridaDTO(Long produtoId, String produtoNome, String categoriaNome, UnidadeMedida unidadeMedida,
        BigDecimal saldoAtual, BigDecimal estoqueMinimo, BigDecimal consumoMedioDiario, int diasReposicao,
        BigDecimal quantidadeSugerida, String origemMedia, int periodoAnaliseDias,
        LocalDate inicioAnalise, LocalDate fimAnalise) {
    public ReposicaoSugeridaDTO(Long produtoId, String produtoNome, String categoriaNome, UnidadeMedida unidadeMedida,
            BigDecimal saldoAtual, BigDecimal estoqueMinimo, BigDecimal consumoMedioDiario, int diasReposicao,
            BigDecimal quantidadeSugerida) {
        this(produtoId, produtoNome, categoriaNome, unidadeMedida, saldoAtual, estoqueMinimo, consumoMedioDiario,
                diasReposicao, quantidadeSugerida, "MANUAL", 30, null, null);
    }
}