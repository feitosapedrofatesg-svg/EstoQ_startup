package com.estoq.business.balancos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ContagemRequestDTO(
        @NotNull @DecimalMin("0") BigDecimal quantidadeFisica,
        @NotNull(message = "Recarregue a contagem antes de salvar.") @jakarta.validation.constraints.PositiveOrZero Long version) {
}