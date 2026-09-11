package com.estoq.business.relatorios;

import java.math.BigDecimal;
import java.time.YearMonth;

public record CmvMensalDTO(YearMonth periodo, BigDecimal cmv, BigDecimal valorCompras, BigDecimal valorDesperdicio) {
}