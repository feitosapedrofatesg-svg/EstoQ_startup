package com.estoq.business.desperdicios;

import com.estoq.core.exceptions.FieldValidationException;

import org.springframework.stereotype.Component;

@Component
public class DesperdicioValidation {

    public void validar(MotivoDesperdicio motivo, String descricao) {
        if (motivo == null) {
            throw new FieldValidationException("motivo", "O motivo do desperdício é obrigatório.");
        }
        if (motivo == MotivoDesperdicio.OUTRO && (descricao == null || descricao.isBlank())) {
            throw new FieldValidationException("descricaoMotivo", "Descreva o motivo quando selecionar OUTRO.");
        }
    }
}