package com.estoq.business.produtos;

import java.math.BigDecimal;

public record EstoqueDTO(Long produtoId, String produtoNome, String categoriaNome, UnidadeMedida unidadeMedida,
        BigDecimal saldoAtual, BigDecimal saldoDisponivelConsumo, BigDecimal valorEstoque, BigDecimal estoqueMinimo, BigDecimal estoqueMedio,
        BigDecimal estoqueMaximo, boolean abaixoDoMinimo, boolean possuiItensAbertos, boolean possuiLotesVencidos, boolean proximoVencimento) {
    public EstoqueDTO(Long produtoId,String produtoNome,String categoriaNome,UnidadeMedida unidadeMedida,
        BigDecimal saldoAtual,BigDecimal saldoDisponivelConsumo,BigDecimal valorEstoque,BigDecimal estoqueMinimo,
        BigDecimal estoqueMedio,BigDecimal estoqueMaximo,boolean abaixoDoMinimo,boolean possuiItensAbertos) {
        this(produtoId,produtoNome,categoriaNome,unidadeMedida,saldoAtual,saldoDisponivelConsumo,valorEstoque,
            estoqueMinimo,estoqueMedio,estoqueMaximo,abaixoDoMinimo,possuiItensAbertos,false,false);
    }
}
