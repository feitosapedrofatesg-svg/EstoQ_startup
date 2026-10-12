package com.estoq.business.balancos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** A versão identifica exatamente o balanço que o usuário consultou. */
public record VersaoBalancoRequestDTO(
        @NotNull(message = "Recarregue a tela para consultar a versão atual.") @PositiveOrZero Long version) {
}
