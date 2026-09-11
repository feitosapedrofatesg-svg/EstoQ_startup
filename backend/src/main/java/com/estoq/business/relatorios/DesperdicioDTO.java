package com.estoq.business.relatorios;

import com.estoq.business.desperdicios.MotivoDesperdicio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DesperdicioDTO(LocalDateTime dataHora, MotivoDesperdicio motivo, String descricaoMotivo,
        Long produtoId, String produtoNome, String loteCodigo, BigDecimal quantidade, BigDecimal valorPrejuizo) {
}