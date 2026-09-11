package com.estoq.business.produtos;

import java.math.BigDecimal;

public record EstoqueDTO(Long produtoId, String produtoNome, String categoriaNome, UnidadeMedida unidadeMedida,
        BigDecimal saldoAtual, BigDecimal valorEstoque, BigDecimal estoqueMinimo, BigDecimal estoqueMedio,
        BigDecimal estoqueMaximo, boolean abaixoDoMinimo, boolean possuiItensAbertos) {
}