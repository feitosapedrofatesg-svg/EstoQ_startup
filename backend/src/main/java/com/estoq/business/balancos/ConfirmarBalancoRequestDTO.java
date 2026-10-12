package com.estoq.business.balancos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/** Encerrar exige conferir também as versões de todas as contagens apresentadas. */
public record ConfirmarBalancoRequestDTO(
        @NotNull @PositiveOrZero Long version,
        @NotNull List<@NotNull @Valid ItemVersion> itens) {
    public record ItemVersion(@NotNull @Positive Long id, @NotNull @PositiveOrZero Long version) {}
}
