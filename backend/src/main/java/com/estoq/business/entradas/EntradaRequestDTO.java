package com.estoq.business.entradas;

import com.estoq.business.produtos.UnidadeMedida;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EntradaRequestDTO(
        @NotNull Long produtoId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3) BigDecimal quantidade,
        @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal valorTotalPago,
        @NotNull UnidadeMedida unidadeCompra,
        LocalDate dataValidade,
        @Size(max = 500) String observacao,
        Boolean semCusto) {
}