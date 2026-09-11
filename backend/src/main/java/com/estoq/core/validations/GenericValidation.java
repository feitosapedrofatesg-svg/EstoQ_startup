package com.estoq.core.validations;

import com.estoq.core.domains.BaseModel;
import com.estoq.core.exceptions.FieldValidationException;

import java.math.BigDecimal;

public abstract class GenericValidation<E extends BaseModel> implements IGenericValidation<E> {

    protected void obrigatorio(Object valor, String campo) {
        if (valor == null || valor instanceof String texto && texto.isBlank()) {
            throw new FieldValidationException(campo, "O campo " + campo + " é obrigatório.");
        }
    }

    protected void naoNegativo(BigDecimal valor, String campo) {
        obrigatorio(valor, campo);
        if (valor.signum() < 0) {
            throw new FieldValidationException(campo, campo + " não pode ser negativo.");
        }
    }
}