package com.estoq.business.desperdicios;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DesperdicioRequestDTO(
        @NotNull Long produtoId,
        Long loteId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 3) BigDecimal quantidade,
        @NotNull MotivoDesperdicio motivo,
        @Size(max = 500) String descricaoMotivo,
        Long versionLote,
        @Size(max = 500) String observacao) {
}