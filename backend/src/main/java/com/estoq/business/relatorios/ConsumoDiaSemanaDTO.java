package com.estoq.business.relatorios;

import java.math.BigDecimal;
import java.time.DayOfWeek;

public record ConsumoDiaSemanaDTO(DayOfWeek diaSemana, BigDecimal quantidadeTotal, BigDecimal quantidadeMedia) {
}