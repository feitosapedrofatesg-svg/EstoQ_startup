package com.estoq.business.relatorios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FaturamentoPeriodoDTO(LocalDate inicio, LocalDate fim, BigDecimal totalInformado,
        int diasInformados, int diasPendentes, BigDecimal receitaBase, boolean receitaPorPeriodo,
        List<FaturamentoDiarioDTO> dias) {
}
