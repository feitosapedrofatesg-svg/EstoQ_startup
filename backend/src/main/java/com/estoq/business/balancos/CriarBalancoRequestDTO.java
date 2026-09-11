package com.estoq.business.balancos;

public record CriarBalancoRequestDTO(TipoBalanco tipo) {

    public CriarBalancoRequestDTO {
        if (tipo == null) {
            tipo = TipoBalanco.GERAL;
        }
    }
}