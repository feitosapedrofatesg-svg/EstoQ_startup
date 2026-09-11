package com.estoq.business.relatorios;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CmvResumoDTO(LocalDateTime dataInicio, LocalDateTime dataFim, BigDecimal valorEstoqueInicial,
        BigDecimal valorCompras, BigDecimal valorEstoqueFinal, BigDecimal cmv, BigDecimal receitaBase,
        BigDecimal cmvPercentual, BigDecimal percentualIdeal, BigDecimal diferencaPercentualParaMeta,
        BigDecimal valorConsumoRegistrado, BigDecimal valorDesperdicio, BigDecimal percentualDesperdicioSobreCmv,
        BigDecimal valorPerdasNaoExplicadas) {
}