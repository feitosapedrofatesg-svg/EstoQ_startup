package com.estoq.business.relatorios;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CmvReceitaBaseDTO(LocalDate inicio, LocalDate fim, BigDecimal receitaBase) {
}