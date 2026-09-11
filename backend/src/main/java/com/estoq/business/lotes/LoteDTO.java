package com.estoq.business.lotes;

import com.estoq.business.produtos.UnidadeMedida;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoteDTO(Long id, Long version, String codigo, Long produtoId, String produtoNome,
        UnidadeMedida unidadeMedida, BigDecimal quantidadeInicial, BigDecimal quantidadeAtual,
        LocalDate dataEntrada, LocalDate dataValidade, BigDecimal precoUnitario, boolean vencido,
        boolean disponivel, Long diasParaVencimento) {
}