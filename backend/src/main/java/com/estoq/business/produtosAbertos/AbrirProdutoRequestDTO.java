package com.estoq.business.produtosAbertos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AbrirProdutoRequestDTO(
        @NotNull Long produtoId,
        @NotNull Long loteId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 3) BigDecimal quantidadeDaEmbalagem,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 3) BigDecimal quantoUsouAgora,
        Long versionLote,
        @Size(max = 500) String observacao) {
}