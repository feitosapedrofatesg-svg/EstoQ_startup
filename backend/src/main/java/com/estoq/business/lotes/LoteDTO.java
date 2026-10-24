package com.estoq.business.lotes;

import com.estoq.business.produtos.UnidadeMedida;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoteDTO(Long id, Long version, String codigo, Long produtoId, String produtoNome,
        UnidadeMedida unidadeMedida, BigDecimal quantidadeInicial, BigDecimal quantidadeAtual,
        LocalDate dataEntrada, LocalDate dataValidade, BigDecimal precoUnitario, boolean vencido,
        boolean disponivel, Long diasParaVencimento,
        String loteFabricante) {
    public LoteDTO(Long id, Long version, String codigo, Long produtoId, String produtoNome, UnidadeMedida unidadeMedida, BigDecimal quantidadeInicial, BigDecimal quantidadeAtual, LocalDate dataEntrada, LocalDate dataValidade, BigDecimal precoUnitario, boolean vencido, boolean disponivel, Long diasParaVencimento) {
        this(id, version, codigo, produtoId, produtoNome, unidadeMedida, quantidadeInicial, quantidadeAtual, dataEntrada, dataValidade, precoUnitario, vencido, disponivel, diasParaVencimento, null);
    }

}