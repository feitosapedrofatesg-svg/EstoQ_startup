package com.estoq.business.consumos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ConsumoRequestDTO(
        @NotNull Long produtoId,
        Long loteId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 3) BigDecimal quantidade,
        Long versionLote,
        @Size(max = 500) String observacao) {
}