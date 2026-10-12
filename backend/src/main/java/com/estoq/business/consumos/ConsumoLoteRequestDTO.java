package com.estoq.business.consumos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ConsumoLoteRequestDTO(@NotNull UUID chave,
        @NotEmpty @Size(max = 500) List<@NotNull @Valid ConsumoRequestDTO> itens) {
}
