package com.estoq.business.notasFiscais;

import com.estoq.business.produtos.UnidadeMedida;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ConfirmarNotaDTO(@NotNull Long version, @NotEmpty @Size(max=1000) List<@Valid Item> itens,
        boolean custosConferidos) {
    public record Item(@NotNull Long id, boolean selecionado, Long produtoId,
            @Size(max=120) String nomeProduto, Long categoriaId, UnidadeMedida unidadeEstoque,
            BigDecimal quantidadeNota, BigDecimal fator, BigDecimal valorEntrada, LocalDate validade,
            boolean embalagemConfirmada, boolean leituraConferida) { }
}
