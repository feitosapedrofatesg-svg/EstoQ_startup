package com.estoq.business.relatorios;

import java.math.BigDecimal;

public record ConsumoMedioDTO(Long produtoId, String produtoNome, String unidadeMedida,
        BigDecimal totalConsumidoPeriodo, long diasPeriodo, BigDecimal consumoMedioDiario) {
}