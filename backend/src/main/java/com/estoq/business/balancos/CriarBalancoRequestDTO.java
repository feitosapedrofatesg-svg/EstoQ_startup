package com.estoq.business.balancos;

import java.util.List;

public record CriarBalancoRequestDTO(TipoBalanco tipo, List<Long> categorias) {

    public CriarBalancoRequestDTO {
        if (tipo == null) {
            tipo = TipoBalanco.GERAL;
        }
        if (categorias == null) {
            categorias = List.of();
        }
    }

    public CriarBalancoRequestDTO(TipoBalanco tipo) {
        this(tipo, List.of());
    }
}