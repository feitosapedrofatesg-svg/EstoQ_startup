package com.estoq.business.relatorios;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FaturamentoDiarioDTO(@NotNull LocalDate data,
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal valor, Long version) {
}
