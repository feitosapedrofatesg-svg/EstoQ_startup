package com.estoq.business.categorias;

import com.estoq.core.exceptions.FieldValidationException;
import com.estoq.core.validations.GenericValidation;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CategoriaValidation extends GenericValidation<CategoriaModel> implements ICategoriaValidation {

    private final ICategoriaRepository repository;

    @Override
    public void validate(CategoriaModel c) {
        obrigatorio(c.getNome(), "nome");
        boolean duplicada = c.getId() == null ? repository.existsByNomeIgnoreCaseAndAtivoTrue(c.getNome())
                : repository.existsByNomeIgnoreCaseAndAtivoTrueAndIdNot(c.getNome(), c.getId());
        if (duplicada) {
            throw new FieldValidationException("nome", "Já existe uma categoria ativa com este nome.");
        }
    }
}