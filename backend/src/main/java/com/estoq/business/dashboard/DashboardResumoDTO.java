package com.estoq.business.dashboard;

import java.math.BigDecimal;

public record DashboardResumoDTO(long totalProdutosAtivos, long produtosEstoqueBaixo, long lotesProximosVencimento,
        long lotesVencidos, long produtosAbertosAtivos, long balancosPendentes, BigDecimal valorDesperdicioPeriodo,
        BigDecimal cmvPeriodo, BigDecimal cmvPercentual, BigDecimal cmvIdeal, BigDecimal diferencaCmvParaMeta,
        BigDecimal perdasNaoExplicadasPeriodo) {
}