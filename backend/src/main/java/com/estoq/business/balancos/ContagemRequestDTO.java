package com.estoq.business.balancos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ContagemRequestDTO(
        @NotNull @Positive BigDecimal quantidadeFisica) {
}