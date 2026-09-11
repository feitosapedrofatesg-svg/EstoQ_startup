package com.estoq.business.produtosAbertos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ConsumirProdutoAbertoRequestDTO(
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 3) BigDecimal quantidade,
        Long version,
        Long versionLote,
        @Size(max = 500) String observacao) {
}