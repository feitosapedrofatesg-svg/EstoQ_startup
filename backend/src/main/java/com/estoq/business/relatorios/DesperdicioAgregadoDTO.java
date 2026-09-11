package com.estoq.business.relatorios;

import com.estoq.business.desperdicios.MotivoDesperdicio;

import java.math.BigDecimal;

public record DesperdicioAgregadoDTO(Long produtoId, String produtoNome, MotivoDesperdicio motivo,
        BigDecimal quantidade, BigDecimal valorPrejuizo) {
}